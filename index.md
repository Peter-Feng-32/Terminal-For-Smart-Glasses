## Terminal For Tooz Smart Glasses

The goal of this project is to produce a terminal emulator interface for the Tooz Smart-Glasses, a pair of smart-glasses developed by ZEISS.
To find more information about the Tooz, visit the [Tooz Website](https://tooz.com/product/devkit/)

Out of the box, the Tooz Smart-Glasses display 400x640 frames sent by a mobile device at a rate of about 1 FPS.
However, by using a terminal emulator interface with defined areas for characters, we are able to greatly optimize the frame rate by selectively updating small portions of the screen as needed, resulting in a frame rate increase of up to around 10 FPS.

This project is built on code from [Termux](https://termux.com/), the open-source terminal emulator for Android, which provides the terminal emulator functionality needed for the whole project to function.

The basic overview is as follows.
Whenever the terminal emulator receives output, the program checks what output it received and determines how much of the screen needs to be updated.  It then sends a Bluetooth packet containing a frame to the Tooz glasses, which updates the part of the screen accordingly.
Currently, the functionality is very basic: if one character was received, a single character frame, which is only about 19x59 is sent to the glasses, and with the coordinates to place it.  Otherwise, the entire screen is sent to the glasses.
This can obviously be optimized and extended for different situations: For example, if an escape sequence was received that cleared a row, we could just send an update for that row to the glasses.

To see how the frame is encoded into a Bluetooth packet, look at FrameToGlasses.java.
The basic idea is that the Tooz Glasses receives frame data in the form of a JPEG.
So, first the terminal screen is drawn onto a blank canvas, and then the canvas is encoded into a JPEG.  The JPEG is sent to the glasses along with some other metadata(coordinates, timeToDisplay, etc.).

One caveat with this project is that it will fail if the entire screen is continuously being updated at rates faster than 1 FPS.  This is because if the entire screen is updated, there is nothing to optimize, and the program will just send the entire frame to the glasses, which then processes it at 1 FPS.  When the glasses receives too many frames to process, it will just crash.

Finally, I haven't figured out a permanent connection method to the glasses yet.  Right now, the program is just attempting to connect to any paired device starting with the string "tooz".  If you decide to use this project, you can obviously modify it to suit your needs.

### Support or Contact

You can email me at pfeng32@gatech.edu
