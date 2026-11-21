cmake_minimum_required(VERSION 3.22.1)
project(thermocamera_usb C)

add_library(ht102usb SHARED ht102_usb.c)
target_compile_options(ht102usb PRIVATE -Wall -Wextra -O2)
target_link_libraries(ht102usb log)
