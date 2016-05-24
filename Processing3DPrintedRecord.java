import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.opengl.*; 
import unlekker.util.*; 
import unlekker.modelbuilder.*; 
import ec.util.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class Processing3DPrintedRecord extends PApplet {

//txt to stl conversion - 3d printable record
//by Amanda Ghassaei
//Dec 2012
//http://www.instructables.com/id/3D-Printed-Record/

/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
*/







String filename = "soundfile.txt";

//record parameters
float diameter = 11.8f;//diameter of record in inches
float innerHole = 0.286f;//diameter of center hole in inches
float innerRad = 2.35f;//radius of innermost groove in inches
float outerRad = 5.75f;//radius of outermost groove in inches
float recordHeight = 0.06f;//height of top of record (inches)
int recordBottom = 0;//height of bottom of record

//audio parameters
float samplingRate = 44100;//(44.1khz audio initially)
float rpm = 33.3f;//rev per min
float rateDivisor = 4;//how much we are downsampling by

//groove parameters
float amplitude = 24;//amplitude of signal (in 16 micron steps)
float bevel = 0.5f;//bevelled groove edge
float grooveWidth = 2;//in 600dpi pixels
float depth = 6;//measured in 16 microns steps, depth of tops of wave in groove from uppermost surface of record

//printer parameters
float dpi = 600;//objet printer prints at 600 dpi
float micronsPerLayer = 16;//microns per vertical print layer

//global geometry storage
UVertexList recordPerimeterUpper,recordPerimeterLower,recordHoleUpper,recordHoleLower;//storage for perimeter and center hole of record
UVertexList grooveOuterUpper,grooveOuterLower,grooveInnerUpper,grooveInnerLower;//groove vertices
UVertexList lastEdge;//storage for conecting one groove to the next
UGeometry geo = new UGeometry();//place to store geometery of vertices

float secPerMin = 60;//seconds per minute
float thetaIter = (samplingRate*secPerMin)/(rateDivisor*rpm);//how many values of theta per cycle
float incrNum = TWO_PI/thetaIter;//calculcate angular incrementation amount
int samplenum = 0;//which audio sample we are currently on

public void setup(){
  
  scaleVariables();//convert units, initialize etc
  setUpRecordShape();//draw basic shape of record
  drawGrooves(processAudioData());//draw in grooves
  
  //change extension of file name
  if(args.length != 0) {
    filename = args[0];
  }
  String name = filename;
  int dotPos = filename.lastIndexOf(".");
  if (dotPos > 0)
    name = filename.substring(0, dotPos);
  //write stl file from geomtery
  geo.writeSTL(this, name + ".stl");
  
  exit();
}

public float[] processAudioData(){
  //get data out of txt file
  String rawData[] = loadStrings(filename);
  String rawDataString = rawData[0];
  float audioData[] = PApplet.parseFloat(split(rawDataString,','));//separated by commas
  
  //normalize audio data to given bitdepth
  //first find max val
  float maxval = 0;
  for(int i=0;i<audioData.length;i++){
    if (abs(audioData[i])>maxval){
      maxval = abs(audioData[i]);
    }
  }
  //normalize amplitude to max val
  for(int i=0;i<audioData.length;i++){
    audioData[i]*=amplitude/maxval;
  }
  
  return audioData;
}

public void scaleVariables(){
  //convert everything to inches
  float micronsPerInch = 25400;//scalingfactor
  amplitude = amplitude*micronsPerLayer/micronsPerInch;
  depth = depth*micronsPerLayer/micronsPerInch;
  grooveWidth /= dpi;
}

public void setUpRecordShape(){
  //set up storage
  recordPerimeterUpper = new UVertexList();
  recordPerimeterLower = new UVertexList();
  recordHoleUpper = new UVertexList();
  recordHoleLower = new UVertexList();
  
  //get vertices
  for(float theta=0;theta<TWO_PI;theta+=incrNum){
    //outer edge of record
    float perimeterX = diameter/2+diameter/2*cos(theta);
    float perimeterY = diameter/2+diameter/2*sin(theta);
    recordPerimeterUpper.add(perimeterX,perimeterY,recordHeight);
    recordPerimeterLower.add(perimeterX,perimeterY,recordBottom);
    //center hole
    float centerHoleX = diameter/2+innerHole/2*cos(theta);
    float centerHoleY = diameter/2+innerHole/2*sin(theta);
    recordHoleUpper.add(centerHoleX,centerHoleY,recordHeight);
    recordHoleLower.add(centerHoleX,centerHoleY,recordBottom);
  }
  
  //close vertex lists (closed loops)
  recordPerimeterUpper.close();
  recordPerimeterLower.close();
  recordHoleUpper.close();
  recordHoleLower.close();
  
  //connect vertices
  geo.quadStrip(recordHoleUpper,recordHoleLower);
  geo.quadStrip(recordHoleLower,recordPerimeterLower);
  geo.quadStrip(recordPerimeterLower,recordPerimeterUpper);
  
  //to start, outer edge of record is the last egde we need to connect to with the outmost groove
  lastEdge = new UVertexList();
  lastEdge.add(recordPerimeterUpper);
  
  println("record drawn, starting grooves");
}

public void drawGrooves(float[] audioData){
  
  int grooveNum = 0;//which groove we are currently drawing
  
  //set up storage
  grooveOuterUpper = new UVertexList();
  grooveOuterLower = new UVertexList();
  grooveInnerUpper = new UVertexList();
  grooveInnerLower = new UVertexList();
  
  //DRAW GROOVES
  float radius = outerRad;//outermost radius (at 5.75") to start
  float radIncr = (grooveWidth+2*bevel*amplitude)/thetaIter;//calculate radial incrementation amount
  int totalgroovenum = PApplet.parseInt(audioData.length/(rateDivisor*thetaIter));
  
  //first draw starting cap
  UVertexList stop1 = beginStartCap(radius, audioData[0]);
  
  //then spiral groove
  while (rateDivisor*samplenum<(audioData.length-rateDivisor*thetaIter+1)){//while we still have audio to write and we have not reached the innermost groove  //radius>innerRad &&
    
    clearGrooveStorage();
    for(float theta=0;theta<TWO_PI;theta+=incrNum){//for theta between 0 and 2pi
      radius = iter(theta, radius, grooveNum, audioData, radIncr);
    }
    completeGrooveRev(grooveNum, radius, audioData);
    connectVertices(grooveNum);

    if (grooveNum==0){//complete beginning cap if neccesary
      finishStartCap(radius, stop1);
    }
    
    //tell me how much longer
    grooveNum++;
    print(grooveNum);
    print(" of ");
    print(totalgroovenum);
    println(" grooves drawn");
  }
  
  //the locked groove is made out of two intersecting grooves, one that spirals in, and one that creates a perfect circle.
  //the ridge between these grooves gets lower and lower until it disappears and the two grooves become one wide groove.
  radius = drawPenultGroove(radius, grooveNum, audioData, radIncr);//second to last groove
  clearGrooveStorage();
  for(float theta=0;theta<TWO_PI;theta+=incrNum){//draw last groove (circular locked groove)
    iter(theta, radius, grooveNum, null, radIncr);
  }
  completeGrooveRev(grooveNum, radius, null);
  connectVertices(grooveNum);

  geo.quadStrip(lastEdge,recordHoleUpper);//close remaining space between last groove and center hole
}
public float getNextSampleElseZero(float[] audioData){
  float aud;
  if (rateDivisor*samplenum>(audioData.length-1)){
    aud = 0;
  } else  {
    aud = audioData[PApplet.parseInt(rateDivisor*samplenum)];
  }
  samplenum++;//increment sample num
  return aud;
}

public float iter(float theta, float radius, int grooveNum, float[] audioData, float radIncr){
  float sineTheta = sin(theta);
  float cosineTheta = cos(theta);

  //calculate height of groove
  float grooveHeight = recordHeight-depth-amplitude;
  if (audioData!=null) grooveHeight += getNextSampleElseZero(audioData);
  
  
  if (grooveNum==0){
    grooveOuterUpper.add((diameter/2+(radius+amplitude*bevel)*cosineTheta),(diameter/2+(radius+amplitude*bevel)*sineTheta),recordHeight);
  }
  grooveOuterLower.add((diameter/2+radius*cosineTheta),(diameter/2+radius*sineTheta),grooveHeight);
  grooveInnerLower.add((diameter/2+(radius-grooveWidth)*cosineTheta),(diameter/2+(radius-grooveWidth)*sineTheta),grooveHeight);
  grooveInnerUpper.add((diameter/2+(radius-grooveWidth-amplitude*bevel)*cosineTheta),(diameter/2+(radius-grooveWidth-amplitude*bevel)*sineTheta),recordHeight);
  
  return radius - radIncr; 
}

public void completeGrooveRev(int grooveNum, float radius, float[] audioData){
  //add last value to grooves to complete one full rev (theta=0)
  float grooveHeight = recordHeight-depth-amplitude;
  if (audioData!=null) grooveHeight += audioData[PApplet.parseInt(rateDivisor*samplenum)];
  if (grooveNum==0){//if joining a groove to the edge of the record
    grooveOuterUpper.add(grooveInnerUpper.first());
  }
  grooveOuterLower.add(diameter/2+radius,diameter/2,grooveHeight);
  grooveInnerLower.add(diameter/2+(radius-grooveWidth),diameter/2,grooveHeight);
  grooveInnerUpper.add(diameter/2+radius-grooveWidth-amplitude*bevel,diameter/2,recordHeight);
}

public void connectVertices(int grooveNum){
  //connect vertices
  if (grooveNum==0){//if joining a groove to the edge of the record
    geo.quadStrip(lastEdge,grooveOuterUpper);
    geo.quadStrip(grooveOuterUpper,grooveOuterLower);
  }
  else{//if joining a groove to another groove
    geo.quadStrip(lastEdge,grooveOuterLower);
  }
  geo.quadStrip(grooveOuterLower,grooveInnerLower);
  geo.quadStrip(grooveInnerLower,grooveInnerUpper);
  
  //set new last edge
  lastEdge.reset();//clear old data
  lastEdge.add(grooveInnerUpper);
}

public UVertexList beginStartCap(float radius, float firstSample){//this is a tiny piece of geometry that closes off the front end of the groove
  UVertexList stop1 = new UVertexList();
  UVertexList stop2 = new UVertexList();
  float grooveHeight = recordHeight-depth-amplitude+firstSample;
  stop1.add((diameter/2+(radius+amplitude*bevel)),(diameter/2),recordHeight);//outerupper
  stop2.add(diameter/2+radius,diameter/2,grooveHeight);//outerlower
  stop2.add(diameter/2+(radius-grooveWidth),diameter/2,grooveHeight);//innerlower
  stop1.add((diameter/2+(radius-grooveWidth-amplitude*bevel)),diameter/2,recordHeight);//innerupper
  geo.quadStrip(stop1,stop2);//draw triangles
  return stop1;
}

public void finishStartCap(float radius, UVertexList stop1){
  UVertexList stop2 = new UVertexList();
  stop2.add(diameter,diameter/2,recordHeight);//outer perimeter[0]
  stop2.add((diameter/2+radius+amplitude*bevel),(diameter/2),recordHeight);//outer groove edge [2pi]
  //draw triangles
  geo.quadStrip(stop1,stop2);
}

public void clearGrooveStorage(){
  grooveOuterUpper.reset();
  grooveOuterLower.reset();
  grooveInnerUpper.reset();
  grooveInnerLower.reset();
}

public float drawPenultGroove(float radius,int grooveNum, float[] audioData, float radIncr){
  //the locked groove is made out of two intersecting grooves, one that spirals in, and one that creates a perfect circle.
  //the ridge between these grooves gets lower and lower until it disappears and the two grooves become on wide groove.
  float changeTheta = TWO_PI*(0.5f*amplitude)/(amplitude+grooveWidth);//what value of theta to merge two last grooves
  float ridgeDecrNum = TWO_PI*amplitude/(changeTheta*thetaIter);//how fast the ridge height is decreasing
  float ridgeHeight = recordHeight;//center ridge starts at same height as record
  clearGrooveStorage();
  
  UVertexList ridge = new UVertexList();
  float theta;
  for(theta=0;theta<TWO_PI;theta+=incrNum){//draw part of spiral groove, until theta = changeTheta
    if (theta<=changeTheta){
      float sineTheta = sin(theta);
      float cosineTheta = cos(theta);
      ridge.add((diameter/2+(radius-grooveWidth-amplitude*bevel)*cosineTheta),(diameter/2+(radius-grooveWidth-amplitude*bevel)*sineTheta),ridgeHeight);
      radius = iter(theta, radius, grooveNum, audioData, radIncr);
      ridgeHeight -= ridgeDecrNum;
      } else {
        break;//get out of this for loop is theat > changeTheta
      }
  }
  
  //complete rev w/o audio data 
  float grooveHeight = recordHeight-depth-amplitude;//zero point for the groove
  
  float sineTheta = sin(theta);//using theta from where we left off
  float cosineTheta = cos(theta);
  grooveOuterLower.add((diameter/2+radius*cosineTheta),(diameter/2+radius*sineTheta),grooveHeight);
  grooveInnerLower.add((diameter/2+(radius-grooveWidth)*cosineTheta),(diameter/2+(radius-grooveWidth)*sineTheta),grooveHeight);
  ridge.add((diameter/2+(radius-grooveWidth-amplitude*bevel)*cosineTheta),(diameter/2+(radius-grooveWidth-amplitude*bevel)*sineTheta),grooveHeight);
  geo.quadStrip(grooveOuterLower,grooveInnerLower);
  geo.quadStrip(grooveInnerLower,ridge);
  
  for(theta=theta;theta<TWO_PI;theta+=incrNum){//for theta between current position and 2pi
    sineTheta = sin(theta);
    cosineTheta = cos(theta);
    grooveOuterLower.add((diameter/2+radius*cosineTheta),(diameter/2+radius*sineTheta),grooveHeight);    
    ridge.add((diameter/2+radius*cosineTheta),(diameter/2+radius*sineTheta),grooveHeight);    
    radius -= radIncr; 
  } 
  //connect vertices
  geo.quadStrip(lastEdge,grooveOuterLower);
  
  //set new last edge
  lastEdge.reset();//clear old data
  lastEdge.add(ridge);
  
  return radius;
}
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "Processing3DPrintedRecord" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
