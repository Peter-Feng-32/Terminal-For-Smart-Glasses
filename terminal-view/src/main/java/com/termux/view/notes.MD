
3/9/2022:

Problems to solve:
Sometimes its good to send individual updates (eg. 1 character is updated) and sometimes its bad(180 characters updated)

How do we decide to send individual updates, update by row, update entire screen, etc?
    Benchmarking?  What would we benchmark?
How do we generate the jpgs in code for each individual update image?
    Character Set
    Current Cursor Location - Needs to be tracked too for updates.
Scrolling Issue?  Rewrite so we aren't doing renders onDraw?  
    Look at onScreenUpdated? Different renders for onScroll vs onKeyDown?
    onScreenUpdated -> Calls invalidate -> Calls onDraw -> Calls drawing functionality
    Create an enum of different events that could trigger invalidate and then wherever it is called update a variable with the correct enum value? 
        Can we pass values to onDraw?
    Would allow us to track what is causing the draw.
    How to know when we are still scrolling versus when we aren't?  Maybe scrolling is fine but not fling? 
    Or if scrolling, update periodically with another thread?
    Or maybe update when finger-lift and was scrolling?

Thad: Do everything at the terminal emulator level.  Make the optimal decision for each terminal emulator command.
    -> Investigate how stuff at the terminal level interacts with stuff on the Android Screen level.
