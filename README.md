# PitchFXDownloader
A tool to download Pitch F/X data from mlb.com. Written in Java.

http://gd2.mlb.com/components/game/mlb/ <-- fetched from here

The executable can be found in the /bin folder. 

The Downloader takes two parameters, the first is the year that you wish to download, the second is the path to where you want to download the files.

Example:

java -jar Downloader.jar 2014 "c:\Users\me\pitchfx"

NOTE: Downloading an entire year of data took about 30 minutes for me, running on an SSD with a good internet connection.
