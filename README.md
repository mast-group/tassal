TASSAL: Tree-based Autofolding Software Summarization ALgorithm [![Build Status](https://travis-ci.org/mast-group/tassal.svg?branch=master)](https://travis-ci.org/mast-group/tassal)
================
 
TASSAL is a tool for the automatic summarization of source code using autofolding. Autofolding automatically creates a summary of a source code file by folding non-essential code and comment blocks. 

This is an implementation of the code summarizer from our paper:  
*Autofolding for Source Code Summarization*  
J. Fowkes, R. Ranca, M. Allamanis, M. Lapata and C. Sutton. arXiv preprint 1403.4503, 2014.   
http://arxiv.org/abs/1403.4503

Installation in Eclipse
-----------------------

Simply import as a maven project into [Eclipse](https://eclipse.org/) using the *File -> Import...* menu option (note that this requires [m2eclipse](http://eclipse.org/m2e/)). 

Running TASSAL
--------------

The *codesum.lm.tui* package contains both the Java and command line interface.

#### Training the source code topic model

*codesum.lm.tui.TrainTopicModel* trains the underlying topic model. It has the following command line options:

* **-d**   directory containing java projects
* **-w**   working directory where the topic model creates necessary files
* **-i**   (optional)  no. iterations to train the topic model for.

#### Autofolding a source file

*codesum.lm.tui.FoldSourceFile* folds a specified source file. It has the following command line options:

* **-w**    working directory where the topic model creates necessary files (same as above)
* **-f**    souce file to autofold
* **-p**    project containing the file to fold
* **-c**    desired compression ratio for the file
* **-o**   (optional)  where to output the folded file

See the individual file javadocs for information on the Java interface.

You can set command line arguments for the TASSAL interface in Eclipse using the *Run Configurations...* menu option. It's also possible to export a runnable jar using the *File -> Export...* menu option.

Example Usage
-------------

A complete example using the command line interface on a runnable jar.

First clone the ActionBarSherlock project into /tmp/java_projects/

  ```sh
  $ mkdir /tmp/java_projects/
  $ cd /tmp/java_projects/
  $ git clone https://github.com/JakeWharton/ActionBarSherlock.git
  ```

Now you can train the topic model on the java projects in /tmp/java_projects/

  ```sh
$ java -cp tassal.jar codesum.lm.tui.TrainTopicModel   
  -d /tmp/java_projects/  -w /tmp/ -i 100
  ```

This trains the topic model for 100 iterations and outputs the model to /tmp/. We can then fold a specific file 

  ```sh
 $ java -cp tassal.jar codesum.lm.tui.FoldSourceFile     
   -w /tmp/  -c 50 -p ActionBarSherlock 
   -f /tmp/java_projects/ActionBarSherlock/actionbarsherlock/src/com/actionbarsherlock/app/SherlockFragment.java 
   -o /tmp/SherlockFragmentFolded.java 
  ```

which will output the folded file to /tmp/SherlockFragmentFolded.java. 

Bugs
----

Please report any bugs using GitHub's issue tracker.

License
-------

This tool is released under the new BSD license.
