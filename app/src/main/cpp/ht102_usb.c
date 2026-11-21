/*
 * Minimal isochronous IN reader over Linux usbfs for the HT-102 UVC stream.
 *
 * Android's Java USB API cannot do isochronous transfers, so this file does only that:
 * submit a ring of ISO URBs on the file descriptor obtained from UsbDeviceConnection,
 * wait for completed ones, copy their packets out to Kotlin and resubmit them.
 * Everything else (UVC probe/commit, alternate setting, payload headers, frame assembly)
 * is done in Kotlin. Read-only: no data is ever sent to the device from here.
 */
#include <jni.h>
#include <errno.h>
#include <poll.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <android/log.h>

#define TAG "ht102usb"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef struct {
    int fd;
    unsigned char endpoint;
    int packet_size;
    int packets_per_urb;
    int num_urbs;
    struct usbdevfs_urb **urbs;
    unsigned char **buffers;
    int submitted;
} Reader;

static struct usbdevfs_urb *alloc_urb(int packets) {
    size_t size = sizeof(struct usbdevfs_urb) + (size_t) packets * sizeof(struct usbdevfs_iso_packet_desc);
    return (struct usbdevfs_urb *) calloc(1, size);
}

static int submit(Reader *r, int i) {
    struct usbdevfs_urb *u = r->urbs[i];
    memset(u, 0, sizeof(struct usbdevfs_urb) + (size_t) r->packets_per_urb * sizeof(struct usbdevfs_iso_packet_desc));
    u->type = USBDEVFS_URB_TYPE_ISO;
    u->endpoint = r->endpoint;
    u->flags = USBDEVFS_URB_ISO_ASAP;
    u->buffer = r->buffers[i];
    u->buffer_length = r->packet_size * r->packets_per_urb;
    u->number_of_packets = r->packets_per_urb;
    u->usercontext = (void *) (intptr_t) i;
    for (int p = 0; p < r->packets_per_urb; p++) u->iso_frame_desc[p].length = (unsigned int) r->packet_size;
    if (ioctl(r->fd, USBDEVFS_SUBMITURB, u) < 0) {
        LOGE("SUBMITURB %d failed: %s", i, strerror(errno));
        return -errno;
    }
    r->submitted++;
    return 0;
}

static void free_reader(Reader *r) {
    if (!r) return;
    for (int i = 0; i < r->num_urbs; i++) {
        if (r->urbs) free(r->urbs[i]);
        if (r->buffers) free(r->buffers[i]);
    }
    free(r->urbs);
    free(r->buffers);
    free(r);
}

/** Returns a handle (>0) or a negative errno. */
JNIEXPORT jlong JNICALL
Java_io_github_mpridal_thermocamera_camera_IsoReader_nativeOpen(
        JNIEnv *env, jclass clazz, jint fd, jint endpoint, jint packetSize, jint packetsPerUrb, jint numUrbs) {
    (void) env; (void) clazz;
    Reader *r = (Reader *) calloc(1, sizeof(Reader));
    if (!r) return -ENOMEM;
    r->fd = fd;
    r->endpoint = (unsigned char) endpoint;
    r->packet_size = packetSize;
    r->packets_per_urb = packetsPerUrb;
    r->num_urbs = numUrbs;
    r->urbs = (struct usbdevfs_urb **) calloc((size_t) numUrbs, sizeof(void *));
    r->buffers = (unsigned char **) calloc((size_t) numUrbs, sizeof(void *));
    if (!r->urbs || !r->buffers) { free_reader(r); return -ENOMEM; }
    for (int i = 0; i < numUrbs; i++) {
        r->urbs[i] = alloc_urb(packetsPerUrb);
        r->buffers[i] = (unsigned char *) malloc((size_t) packetSize * (size_t) packetsPerUrb);
        if (!r->urbs[i] || !r->buffers[i]) { free_reader(r); return -ENOMEM; }
    }
    for (int i = 0; i < numUrbs; i++) {
        int rc = submit(r, i);
        if (rc < 0) {
            // Discard what was submitted and give up.
            for (int k = 0; k < i; k++) ioctl(fd, USBDEVFS_DISCARDURB, r->urbs[k]);
            for (int k = 0; k < i; k++) { void *done; ioctl(fd, USBDEVFS_REAPURB, &done); }
            free_reader(r);
            return rc;
        }
    }
    return (jlong) (intptr_t) r;
}

/**
 * Waits up to timeoutMs for one completed URB. Copies its packets into out (packet i at
 * offset i * packetSize) and their actual lengths into lengths (-1 = packet error), then
 * resubmits it. Returns the number of packets, 0 on timeout, or a negative errno
 * (-ENODEV when the camera was unplugged).
 */
JNIEXPORT jint JNICALL
Java_io_github_mpridal_thermocamera_camera_IsoReader_nativeRead(
        JNIEnv *env, jclass clazz, jlong handle, jbyteArray out, jintArray lengths, jint timeoutMs) {
    (void) clazz;
    Reader *r = (Reader *) (intptr_t) handle;
    if (!r) return -EINVAL;
    struct pollfd pfd = {.fd = r->fd, .events = POLLOUT | POLLWRNORM};
    int pr = poll(&pfd, 1, timeoutMs);
    if (pr == 0) return 0;
    if (pr < 0) return errno == EINTR ? 0 : -errno;
    if (pfd.revents & (POLLERR | POLLHUP)) return -ENODEV;

    struct usbdevfs_urb *done = NULL;
    if (ioctl(r->fd, USBDEVFS_REAPURBNDELAY, &done) < 0) {
        if (errno == EAGAIN) return 0;
        return -errno;
    }
    r->submitted--;
    int idx = (int) (intptr_t) done->usercontext;
    int n = done->number_of_packets;
    if (done->status == -ENODEV || done->status == -ESHUTDOWN) return -ENODEV;

    jint *lens = (*env)->GetIntArrayElements(env, lengths, NULL);
    jbyte *dst = (*env)->GetByteArrayElements(env, out, NULL);
    for (int p = 0; p < n; p++) {
        struct usbdevfs_iso_packet_desc *d = &done->iso_frame_desc[p];
        if (d->status != 0) {
            lens[p] = -1;
        } else {
            lens[p] = (jint) d->actual_length;
            memcpy(dst + (size_t) p * r->packet_size, r->buffers[idx] + (size_t) p * r->packet_size, d->actual_length);
        }
    }
    (*env)->ReleaseByteArrayElements(env, out, dst, 0);
    (*env)->ReleaseIntArrayElements(env, lengths, lens, 0);

    int rc = submit(r, idx);
    if (rc < 0) return rc;
    return n;
}

/** Cancels all URBs, collects them back and frees memory. The fd itself is closed by Kotlin. */
JNIEXPORT void JNICALL
Java_io_github_mpridal_thermocamera_camera_IsoReader_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    Reader *r = (Reader *) (intptr_t) handle;
    if (!r) return;
    for (int i = 0; i < r->num_urbs; i++) ioctl(r->fd, USBDEVFS_DISCARDURB, r->urbs[i]);
    // Reap everything that is still owned by the kernel; stop on error (device gone).
    while (r->submitted > 0) {
        struct usbdevfs_urb *done = NULL;
        struct pollfd pfd = {.fd = r->fd, .events = POLLOUT | POLLWRNORM};
        if (poll(&pfd, 1, 200) <= 0) break;
        if (ioctl(r->fd, USBDEVFS_REAPURBNDELAY, &done) < 0) {
            if (errno == EAGAIN) continue;
            break;
        }
        r->submitted--;
    }
    if (r->submitted > 0) {
        // The kernel still owns buffers (device unplugged); leak them rather than risk a use-after-free.
        LOGE("%d URBs not reaped on close", r->submitted);
        return;
    }
    free_reader(r);
}
