## Terminal For Tooz Smart Glasses

### About
The goal of this project is to produce a terminal emulator interface for the Tooz DevKit, a pair of smart-glasses developed by ZEISS.
To find more information about the Tooz, visit the [Tooz Website](https://tooz.com/product/devkit/).  The intended use case is captioning for people who are Deaf/hard of hearing.

Out of the box, the Tooz Smart-Glasses display 400x640 frames sent by a mobile device at a rate of about 1 FPS.
However, by using a terminal emulator interface with defined areas for characters, we are able to greatly optimize the frame rate by selectively updating small portions of the screen as needed, resulting in a frame rate increase of up to around 10 FPS.

This project is built on code from [Termux](https://termux.com/), the open-source terminal emulator for Android, which provides the terminal emulator functionality needed for the whole project to function.

### Overview

The basic overview is as follows.
Whenever the terminal emulator receives output, the program checks what output it received and determines how much of the screen needs to be updated by drawing a minimum size bounding box around the updated area.  It then sends a Bluetooth packet containing a frame to the Tooz glasses, which updates the part of the screen accordingly.

The basic idea is that since Tooz Glasses receives frame data in the form of a JPEG, first the terminal screen is drawn onto a blank canvas, and then the canvas is encoded into a JPEG.  The JPEG is sent to the glasses along with some other metadata(coordinates, timeToDisplay, etc.).

This is integrated with Google's Live Transcribe Speech Engine which allows us to fetch subtitles from their speech-to-text API.  Using this, we can provide consistent, conversation-speed live captioning on the Tooz DevKit.

## Installation and Configuration

Installing this project is quite simple.
You can just install Android Studio onto your computer, clone this repository, and use Android Studio to install this project on your Android device.

## Contact

You can email me at pfeng32@gatech.edu
