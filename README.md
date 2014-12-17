Autofolding for Source Code Summarization [![Build Status](https://travis-ci.org/mast-group/autofolding.svg?branch=master)](https://travis-ci.org/mast-group/autofolding)
================
 
An implementation of the code summarizer from the paper:  
Autofolding for Source Code Summarization  
J. Fowkes, R. Ranca, M. Allamanis, M. Lapata and C. Sutton. arXiv preprint 1403.4503, 2014.  


Installation in Eclipse
------

Simply import as a maven project into Eclipse (this requires m2eclipse). 


Interface
------

codesum.lm.tui contains the command line interface:

  * codesum.lm.tui.TrainTopicModel trains the underlying topic model
  * codesum.lm.tui.FoldSourceFile folds a specified source file


Example Usage
------------

Complete example using the command line interface (assuming the ActionBarSherlock project cloned into /tmp/java_projects/):

  ```
  java -cp target/autofolding-1.1-SNAPSHOT.jar codesum.lm.tui.TrainTopicModel -w /tmp/ -d /tmp/java_projects/ -i 100

  java -cp target/autofolding-1.1-SNAPSHOT.jar codesum.lm.tui.FoldSourceFile -c 50 -f /tmp/java_projects/ActionBarSherlock/actionbarsherlock/src/com/actionbarsherlock/ActionBarSherlock.java -o /tmp/folds.txt -p ActionBarSherlock -w /tmp/
  ```

This tool is released under a BSD license.
