##//converts sound files to txt for LP printing
##//Original code was from Amanda Ghassaei:
##//http://www.instructables.com/id/3D-Printed-Record/
## 
##/*
## * This program is free software; you can redistribute it and/or modify
## * it under the terms of the GNU General Public License as published by
## * the Free Software Foundation; either version 3 of the License, or
## * (at your option) any later version.
##*/



## Usage: python wavtotext.py  your_sound_file_here
## Full Install Instructions: 
## 1) Get virtualenv
## 2) Start a virtual environment in the root folder of the repo using "virtualenv venv"
## 3) Activate the virtual environment with "activate venv/bin/activate"
## 4) Install pydub via "pip install pydub"
## 5) Run the python script via the command above at "Usage:"
import os
import sys
import wave
import math
import struct
from pydub import AudioSegment

bitDepth = 8#target bitDepth
frate = 44100#target frame rate


def main():

    fileName = sys.argv[1] #file to be imported

    if fileName[-4:] == ".mp3": #convert mp3 files

    	#current strategy is to re-save the file as .wav
    	#change the file name, and proceede as normal
    	#should probably make this less jank in the future
    	path = os.getcwd()
    	sound = AudioSegment.from_mp3(path + "/" + fileName)
    	sound.export(path + "/" + fileName[:-4] + ".wav", format="wav")
    	full_filename = fileName[:-4] + ".wav" #once 

	#read file and get data
	w = wave.open(full_filename, 'r')
	numframes = w.getnframes()

	frame = w.readframes(numframes)#w.getnframes()
	frameInt = map(ord, list(frame))#turn into array

	#separate left and right channels and merge bytes
	frameOneChannel = [0]*numframes#initialize list of one channel of wave
	for i in range(numframes):
	    frameOneChannel[i] = frameInt[4*i+1]*2**8+frameInt[4*i]#separate channels and store one channel in new list
	    if frameOneChannel[i] > 2**15:
	        frameOneChannel[i] = (frameOneChannel[i]-2**16)
	    elif frameOneChannel[i] == 2**15:
	        frameOneChannel[i] = 0
	    else:
	        frameOneChannel[i] = frameOneChannel[i]

	#convert to string
	audioStr = ''
	for i in range(numframes):
	    audioStr += str(frameOneChannel[i])
	    audioStr += ","#separate elements with comma

	split_filename = full_filename[:-3]#remove .wav extension
	text_file = open(split_filename+"txt", "w")
	text_file.write("%s"%audioStr)
	text_file.close()

if __name__ == "__main__":
    main()


