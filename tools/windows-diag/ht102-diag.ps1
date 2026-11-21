# HT-102 diagnostika pro projekt ThermoCamera
# Skript jen cte informace o kamere a zachyti par snimku do slozky "vysledky".
# Nic neinstaluje, nic v systemu nemeni.
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$out = Join-Path $root 'vysledky'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$log = Join-Path $out 'report.txt'
Set-Content -Path $log -Value ("== HT-102 diagnostika " + (Get-Date -Format s) + " ==") -Encoding UTF8

function W([string]$s) {
    Add-Content -Path $log -Value $s -Encoding UTF8
    Write-Host $s
}

function Err($er) {
    $e = $er.Exception
    while ($e.InnerException) { $e = $e.InnerException }
    return ("{0} (0x{1:X8})" -f $e.Message, $e.HResult)
}

W ("PowerShell {0}; LanguageMode={1}; OS={2}; 64bit={3}" -f $PSVersionTable.PSVersion, $ExecutionContext.SessionState.LanguageMode, [Environment]::OSVersion.VersionString, [Environment]::Is64BitProcess)

$helperOk = $false
try {
    Add-Type -Path (Join-Path $root 'Ht102Diag.cs') -ReferencedAssemblies System.Drawing -ErrorAction Stop
    $helperOk = $true
} catch {
    W ("Pomocny kod se nepodarilo nacist: " + (Err $_))
}

# ---------------------------------------------------------------- 1) PnP
W ""
W "### 1) Zarizeni v systemu (PnP)"
$devs = @(Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue | Where-Object { $_.InstanceId -match 'VID_0416&PID_9393' -or $_.FriendlyName -match 'W55FA93' })
if ($devs.Count -eq 0) { W "Kamera nenalezena - je zapojena?" }
$keys = @('DEVPKEY_Device_HardwareIds', 'DEVPKEY_Device_CompatibleIds', 'DEVPKEY_Device_Service', 'DEVPKEY_Device_Class',
          'DEVPKEY_Device_Parent', 'DEVPKEY_Device_Children', 'DEVPKEY_Device_LocationInfo', 'DEVPKEY_Device_Address',
          'DEVPKEY_Device_BusReportedDeviceDesc', 'DEVPKEY_Device_Manufacturer', 'DEVPKEY_Device_DriverInfPath',
          'DEVPKEY_Device_DriverVersion', 'DEVPKEY_Device_DriverProvider', 'DEVPKEY_Device_Stack',
          'DEVPKEY_Device_UpperFilters', 'DEVPKEY_Device_LowerFilters')
foreach ($d in $devs) {
    W ""
    W ("--- {0} | trida={1} | stav={2}" -f $d.FriendlyName, $d.Class, $d.Status)
    W ("    {0}" -f $d.InstanceId)
    foreach ($k in $keys) {
        $p = Get-PnpDeviceProperty -InstanceId $d.InstanceId -KeyName $k -ErrorAction SilentlyContinue
        if ($p -and ($null -ne $p.Data) -and ("$($p.Data)" -ne '')) {
            W ("    {0} = {1}" -f $k.Replace('DEVPKEY_Device_', ''), ((@($p.Data) | ForEach-Object { "$_" }) -join ' | '))
        }
    }
}

# ---------------------------------------------------------------- 2) USB deskriptory
W ""
W "### 2) USB deskriptory"
$usbDev = $devs | Where-Object { $_.InstanceId -match '^USB\\VID_0416&PID_9393' -and $_.InstanceId -notmatch '&MI_' } | Select-Object -First 1
if (-not $helperOk) {
    W "preskoceno (chybi pomocny kod)"
} elseif (-not $usbDev) {
    W "USB zarizeni VID_0416&PID_9393 nenalezeno"
} else {
    try {
        $parent = (Get-PnpDeviceProperty -InstanceId $usbDev.InstanceId -KeyName DEVPKEY_Device_Parent).Data
        $port = [int](Get-PnpDeviceProperty -InstanceId $usbDev.InstanceId -KeyName DEVPKEY_Device_Address).Data
        $hub = [Ht102Diag]::HubPath($parent)
        W ("hub={0} port={1}" -f $parent, $port)
        if (-not $hub) { throw "cesta k hubu nenalezena" }
        $dd = [Ht102Diag]::GetDescriptor($hub, $port, 1, 0, 0, 18)
        W ("DEVICE: " + [Ht102Diag]::Hex($dd, 0, $dd.Length))
        $c9 = [Ht102Diag]::GetDescriptor($hub, $port, 2, 0, 0, 9)
        $total = [int]$c9[2] + 256 * [int]$c9[3]
        $cfg = [Ht102Diag]::GetDescriptor($hub, $port, 2, 0, 0, $total)
        W ("CONFIG ({0} B):" -f $cfg.Length)
        W ([Ht102Diag]::SplitDescriptors($cfg))
        [IO.File]::WriteAllBytes((Join-Path $out 'config-descriptor.bin'), $cfg)
        $lang = 0x0409
        try {
            $s0 = [Ht102Diag]::GetDescriptor($hub, $port, 3, 0, 0, 255)
            if ($s0.Length -ge 4) { $lang = [int]$s0[2] + 256 * [int]$s0[3] }
        } catch { }
        for ($sidx = 1; $sidx -le 12; $sidx++) {
            try {
                $sd = [Ht102Diag]::GetDescriptor($hub, $port, 3, $sidx, $lang, 255)
                if ($sd.Length -gt 2) { W ("STRING {0}: {1}" -f $sidx, [Text.Encoding]::Unicode.GetString($sd, 2, $sd.Length - 2)) }
            } catch { }
        }
    } catch {
        W ("Deskriptory se nepodarilo precist: " + (Err $_))
    }
}

# ---------------------------------------------------------------- 3) Nastroje
W ""
W "### 3) Nastroje"
$ff = Get-Command ffmpeg -ErrorAction SilentlyContinue | Select-Object -First 1
$py = Get-Command python -ErrorAction SilentlyContinue | Select-Object -First 1
W ("ffmpeg: " + $(if ($ff) { $ff.Source } else { 'nenalezen' }))
W ("python: " + $(if ($py) { $py.Source } else { 'nenalezen' }))

# ---------------------------------------------------------------- 4) Formaty a snimky
W ""
W "### 4) Formaty a snimky (Windows MediaCapture)"
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.Capture.MediaCapture, Windows.Media.Capture, ContentType = WindowsRuntime]
    $null = [Windows.Media.Capture.MediaCaptureInitializationSettings, Windows.Media.Capture, ContentType = WindowsRuntime]
    $null = [Windows.Media.Capture.Frames.MediaFrameSourceGroup, Windows.Media.Capture, ContentType = WindowsRuntime]
    $null = [Windows.Media.Capture.Frames.MediaFrameReader, Windows.Media.Capture, ContentType = WindowsRuntime]
    $null = [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.Buffer, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Graphics.Imaging.BitmapBufferAccessMode, Windows.Graphics.Imaging, ContentType = WindowsRuntime]

    $methods = [System.WindowsRuntimeSystemExtensions].GetMethods()
    $asTaskOp = $methods | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
    $asTaskAct = $methods | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction' } | Select-Object -First 1

    function AwaitOp($op, [Type]$t) {
        $task = $asTaskOp.MakeGenericMethod($t).Invoke($null, @($op))
        if (-not $task.Wait(15000)) { throw "vyprsel cas" }
        return $task.Result
    }
    function AwaitAct($a) {
        $task = $asTaskAct.Invoke($null, @($a))
        if (-not $task.Wait(15000)) { throw "vyprsel cas" }
    }
    function BufToBytes($b) {
        $len = [int]$b.Length
        $arr = New-Object byte[] $len
        $dr = [Windows.Storage.Streams.DataReader]::FromBuffer($b)
        $dr.ReadBytes($arr)
        $dr.Dispose()
        return ,$arr
    }

    $listType = [System.Collections.Generic.IReadOnlyList[Windows.Media.Capture.Frames.MediaFrameSourceGroup]]
    $groups = @(AwaitOp ([Windows.Media.Capture.Frames.MediaFrameSourceGroup]::FindAllAsync()) $listType)
    foreach ($g in $groups) {
        W ("Skupina: {0}" -f $g.DisplayName)
        foreach ($inf in $g.SourceInfos) { W ("   zdroj: {0} / {1}" -f $inf.SourceKind, $inf.MediaStreamType) }
    }
    $grp = $groups | Where-Object { $_.DisplayName -match 'W55FA93' } | Select-Object -First 1
    if (-not $grp) { $grp = $groups | Where-Object { $_.Id -match 'VID_0416&PID_9393' } | Select-Object -First 1 }
    if (-not $grp) { throw "skupina kamery W55FA93 nenalezena" }

    $set = [Windows.Media.Capture.MediaCaptureInitializationSettings]::new()
    $set.SourceGroup = $grp
    $set.SharingMode = [Windows.Media.Capture.MediaCaptureSharingMode]::ExclusiveControl
    $set.MemoryPreference = [Windows.Media.Capture.MediaCaptureMemoryPreference]::Cpu
    $set.StreamingCaptureMode = [Windows.Media.Capture.StreamingCaptureMode]::Video
    $mc = [Windows.Media.Capture.MediaCapture]::new()
    AwaitAct ($mc.InitializeAsync($set))

    $sources = @()
    foreach ($kv in $mc.FrameSources) { $sources += $kv.Value }
    foreach ($src in $sources) {
        W ("FrameSource: {0} / {1}" -f $src.Info.SourceKind, $src.Info.MediaStreamType)
        foreach ($f in $src.SupportedFormats) {
            W ("   {0} {1}x{2} @ {3}/{4} fps" -f $f.Subtype, $f.VideoFormat.Width, $f.VideoFormat.Height, $f.FrameRate.Numerator, $f.FrameRate.Denominator)
        }
    }

    $src = $sources | Select-Object -First 1
    $bppMap = @{ 'Yuy2' = 2.0; 'Bgra8' = 4.0; 'Rgba8' = 4.0; 'Gray8' = 1.0; 'Gray16' = 2.0; 'Nv12' = 1.5 }
    $seen = @{}
    $fmtCount = 0
    foreach ($f in @($src.SupportedFormats)) {
        $key = "{0}_{1}x{2}" -f $f.Subtype, $f.VideoFormat.Width, $f.VideoFormat.Height
        if ($seen.ContainsKey($key) -or $fmtCount -ge 8) { continue }
        $seen[$key] = $true
        $fmtCount++
        W ""
        W ("Zachytavam {0}" -f $key)
        try {
            AwaitAct ($src.SetFormatAsync($f))
            $reader = $null
            if ($f.Subtype -eq 'MJPG') {
                try {
                    $reader = AwaitOp ($mc.CreateFrameReaderAsync($src, 'MJPG')) ([Windows.Media.Capture.Frames.MediaFrameReader])
                    W "   ctecka v rezimu MJPG (bez dekodovani)"
                } catch {
                    W ("   MJPG ctecka: " + (Err $_))
                    $reader = $null
                }
            }
            if (-not $reader) { $reader = AwaitOp ($mc.CreateFrameReaderAsync($src)) ([Windows.Media.Capture.Frames.MediaFrameReader]) }
            $st = AwaitOp ($reader.StartAsync()) ([Windows.Media.Capture.Frames.MediaFrameReaderStartStatus])
            W ("   start: {0}" -f $st)
            $n = 0
            $lastTs = $null
            $prevBytes = $null
            $sw = [Diagnostics.Stopwatch]::StartNew()
            while ($sw.ElapsedMilliseconds -lt 6000 -and $n -lt 10) {
                Start-Sleep -Milliseconds 40
                $fr = $reader.TryAcquireLatestFrame()
                if ($null -eq $fr) { continue }
                if (($null -ne $lastTs) -and ($fr.SystemRelativeTime -eq $lastTs)) { continue }
                $lastTs = $fr.SystemRelativeTime
                $base = Join-Path $out ("snimek_{0}_{1}" -f $key, $n)
                $vmf = $fr.VideoMediaFrame
                $sbm = $null
                if ($vmf) { $sbm = $vmf.SoftwareBitmap }
                if ($sbm) {
                    $pf = "$($sbm.BitmapPixelFormat)"
                    $bb = $sbm.LockBuffer([Windows.Graphics.Imaging.BitmapBufferAccessMode]::Read)
                    $pd = $bb.GetPlaneDescription(0)
                    $mref = $bb.CreateReference()
                    $bytes = [Ht102Diag]::ReadMemoryBuffer($mref)
                    try { $mref.Dispose() } catch { }
                    try { $bb.Dispose() } catch { }
                    if ($n -eq 0) { W ("   rovina 0: start={0} stride={1} {2}x{3}" -f $pd.StartIndex, $pd.Stride, $pd.Width, $pd.Height) }
                    [IO.File]::WriteAllBytes($base + '_' + $pf + '.bin', $bytes)
                    W ("   snimek {0}: {1}x{2} {3}, {4} B" -f $n, $sbm.PixelWidth, $sbm.PixelHeight, $pf, $bytes.Length)
                    if ($helperOk) {
                        $png = $null
                        if ($n -eq 0) { $png = $base + '_jas.png' }
                        W ([Ht102Diag]::AnalyzeFrame($bytes, [int]$sbm.PixelWidth, [int]$sbm.PixelHeight, $pf, $png))
                        if ($pf -eq 'Yuy2') { [Ht102Diag]::Yuy2ToRgbPng($bytes, [int]$sbm.PixelWidth, [int]$sbm.PixelHeight, $base + '_barva.png') }
                        W ("   cas snimku: {0}" -f $fr.SystemRelativeTime)
                        if ($null -ne $prevBytes) { W ([Ht102Diag]::FrameDiff($prevBytes, $bytes, [int]$sbm.PixelHeight)) }
                    }
                    $prevBytes = $bytes
                } elseif ($vmf) {
                    W ("   snimek {0}: bez SoftwareBitmap" -f $n)
                }
                try { $fr.Dispose() } catch { }
                $n++
            }
            if ($n -eq 0) { W "   zadny snimek neprisel" }
            try { AwaitAct ($reader.StopAsync()) } catch { }
            try { $reader.Dispose() } catch { }
        } catch {
            W ("   chyba: " + (Err $_))
        }
    }
    # ---- standardni ovladaci prvky kamery (zda nenesou skryta data)
    W ""
    W "### 4b) Ovladaci prvky kamery (2x cteni s odstupem 3 s)"
    $vdc = $mc.VideoDeviceController
    $ctlNames = @('Brightness', 'Contrast', 'Hue', 'WhiteBalance', 'BacklightCompensation', 'Pan', 'Tilt', 'Zoom', 'Roll', 'Exposure', 'Focus')
    for ($round = 1; $round -le 2; $round++) {
        foreach ($cn in $ctlNames) {
            try {
                $ctl = $vdc.$cn
                $cap = $ctl.Capabilities
                if ($cap.Supported) {
                    $val = 0.0
                    $okv = $ctl.TryGetValue([ref]$val)
                    W ("   [{0}] {1}: hodnota={2} (ok={3}) min={4} max={5} krok={6} vychozi={7} auto={8}" -f $round, $cn, $val, $okv, $cap.Min, $cap.Max, $cap.Step, $cap.Default, $cap.AutoModeSupported)
                }
            } catch {
                W ("   [{0}] {1}: chyba {2}" -f $round, $cn, (Err $_))
            }
        }
        if ($round -eq 1) { Start-Sleep -Seconds 3 }
    }
    try { $mc.Dispose() } catch { }
} catch {
    W ("MediaCapture: " + (Err $_))
    W "Tip: Nastaveni Windows > Ochrana osobnich udaju > Fotoaparat > povol pristup desktopovym aplikacim; zavri aplikaci Fotoaparat."
}

# ---------------------------------------------------------------- 5) ffmpeg (jen pokud je nainstalovany)
if ($ff) {
    W ""
    W "### 5) ffmpeg (DirectShow)"
    try {
        $devList = (& $ff.Source -hide_banner -list_devices true -f dshow -i dummy 2>&1 | Out-String)
        W $devList
        $name = 'W55FA93 USB UVC Device'
        if ($devList -match '"([^"]*W55FA93[^"]*)"') { $name = $Matches[1] }
        $opts = (& $ff.Source -hide_banner -list_options true -f dshow -i ("video=" + $name) 2>&1 | Out-String)
        W $opts
        $mj = Join-Path $out 'ffmpeg_mjpeg.mjpg'
        $res = (& $ff.Source -hide_banner -loglevel error -y -f dshow -vcodec mjpeg -i ("video=" + $name) -frames:v 5 -c:v copy -f mjpeg $mj 2>&1 | Out-String)
        if ($res) { W $res }
        if ((Test-Path $mj) -and $helperOk) { W ([Ht102Diag]::JpegInfo([IO.File]::ReadAllBytes($mj))) }
    } catch {
        W ("ffmpeg: " + (Err $_))
    }
}

W ""
W "Hotovo."
