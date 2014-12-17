Autofolding for Code Summarization [![Build Status](https://travis-ci.org/mast-group/autofolding.svg?branch=master)](https://travis-ci.org/mast-group/autofolding)
================
 
An implementation of the code summarizer from the paper:  
*Autofolding for Source Code Summarization*  
J. Fowkes, R. Ranca, M. Allamanis, M. Lapata and C. Sutton. arXiv preprint 1403.4503, 2014.   
http://arxiv.org/abs/1403.4503


Installation in Eclipse
------

Simply import as a maven project into Eclipse (this requires m2eclipse). 


Interface
------

codesum.lm.tui contains the Java and command line interface:

  * codesum.lm.tui.TrainTopicModel trains the underlying topic model
  * codesum.lm.tui.FoldSourceFile folds a specified source file

see the individual files for more information.


Example Usage
------------

A complete example using the command line interface.

First clone the ActionBarSherlock project into /tmp/java_projects/

  ```
  mkdir /tmp/java_projects/
  cd /tmp/java_projects/
  git clone https://github.com/JakeWharton/ActionBarSherlock.git
  ```

Now we can train the topic model on the java projects in /tmp/java_projects/

  ```
  java -cp target/autofolding-1.1-SNAPSHOT.jar codesum.lm.tui.TrainTopicModel -w /tmp/ -d /tmp/java_projects/ -i 100
  ```

This trains the topic model for 100 iterations and outputs the model to /tmp/. We can then fold a specific file 

  ```
  java -cp target/autofolding-1.1-SNAPSHOT.jar codesum.lm.tui.FoldSourceFile -c 50 
  -f /tmp/java_projects/ActionBarSherlock/actionbarsherlock/src/com/actionbarsherlock/ActionBarSherlock.java 
  -o /tmp/ActionBarSherlock_folded.java -p ActionBarSherlock -w /tmp/
  ```

which will output the folded file to /tmp/ActionBarSherlock_folded.java. 


This tool is released under a BSD license.
