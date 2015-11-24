TASSAL: Tree-based Autofolding Software Summarization ALgorithm [![Build Status](https://travis-ci.org/mast-group/tassal.svg?branch=master)](https://travis-ci.org/mast-group/tassal)
================
 
TASSAL is a tool for the automatic summarization of source code using autofolding. Autofolding automatically creates a summary of a source code file by folding non-essential code and comment blocks. 

**NEW:** For a live demo of TASSAL that allows you to summarize any GitHub project see:   
https://code-summarizer.herokuapp.com

This is an implementation of the code summarizer from our paper:  
[*Autofolding for Source Code Summarization*](http://arxiv.org/abs/1403.4503)  
J. Fowkes, R. Ranca, M. Allamanis, M. Lapata and C. Sutton. arXiv preprint 1403.4503, 2015.   

There are two main variants of the algorithm:

* **TASSAL VSM** which uses a [Vector Space Model](https://en.wikipedia.org/wiki/Vector_space_model) for source code - less accurate but very fast (real-time)
* **TASSAL** which uses a [Topic Model](https://en.wikipedia.org/wiki/Topic_model) for source code - more accurate but slower (requires training)

both are described below.

Installation 
------------

#### Installing in Eclipse

Simply import as a maven project into [Eclipse](https://eclipse.org/) using the *File -> Import...* menu option (note that this requires [m2eclipse](http://eclipse.org/m2e/)). 

It's also possible to export a runnable jar from Eclipse using the *File -> Export...* menu option.

#### Compiling a Runnable Jar

To compile a standalone runnable jar, simply run

```
mvn package
```

in the main tassal directory (note that this requires [maven](https://maven.apache.org/)).

This will create the standalone runnable jar ```tassal-1.1-SNAPSHOT.jar``` in the tassal/target subdirectory.

Running TASSAL VSM
------------------

TASAAL VSM uses a Vector Space Model of source code tokens to determine which are the least relevent code regions to autofold. 
TASSAL VSM can run in real-time. 

#### Autofolding a source file

*codesum.lm.tui.FoldSourceFileVSM* folds a specified source file. It has the following command line options:

* **-f**  &nbsp;  souce file to autofold
* **-c**  &nbsp;  desired compression ratio for the file (%)
* **-o**  &nbsp; (optional)  where to save the folded file

See the individual file javadocs in *codesum.lm.tui* for information on the Java interface.
In Eclipse you can set command line arguments for the TASSAL interface using the *Run Configurations...* menu option. 

#### Example Usage

A complete example using the command line interface on a runnable jar.

First clone the ActionBarSherlock project into /tmp/java_projects/

  ```sh 
  $ mkdir /tmp/java_projects/
  $ cd /tmp/java_projects/
  $ git clone https://github.com/JakeWharton/ActionBarSherlock.git 
  ```

We can then fold a specific file 

  ```sh 
  $ java -cp tassal/target/tassal-1.1-SNAPSHOT.jar codesum.lm.tui.FoldSourceFileVSM     
   -c 50
   -f /tmp/java_projects/ActionBarSherlock/actionbarsherlock/src/com/actionbarsherlock/app/SherlockFragment.java 
   -o /tmp/SherlockFragmentFolded.java 
  ```

which will output the folded file to /tmp/SherlockFragmentFolded.java. 

Running TASSAL
--------------

TASAAL uses a scoped Topic Model of source code tokens to determine which are the least relevent code regions to autofold. 
TASSAL requires the topic model to be trained on a dataset (the larger the better) before it can fold files in the dataset. 
While this is slower than using a VSM model, it is considerably more accurate. 

#### Training the source code topic model

*codesum.lm.tui.TrainTopicModel* trains the underlying topic model. It has the following command line options:

* **-d** &nbsp;  directory containing java projects
* **-w** &nbsp;  working directory where the topic model creates necessary files
* **-i** &nbsp;  (optional)  no. iterations to train the topic model for.

This will output a summary of the top 25 tokens in some of the discovered topics. 

#### Autofolding a source file

*codesum.lm.tui.FoldSourceFile* folds a specified source file. It has the following command line options:

* **-w** &nbsp;  working directory where the topic model creates necessary files (same as above)
* **-f** &nbsp;  souce file to autofold
* **-p** &nbsp;  project containing the file to fold
* **-c** &nbsp;  desired compression ratio for the file (%)
* **-b** &nbsp;  (optional)  background topic to back off to (0-2, default=2) 
* **-o** &nbsp;  (optional)  where to save the folded file

See the individual file javadocs in *codesum.lm.tui* for information on the Java interface.
In Eclipse you can set command line arguments for the TASSAL interface using the *Run Configurations...* menu option. 

#### Example Usage

A complete example using the command line interface on a runnable jar.

First clone the ActionBarSherlock project into /tmp/java_projects/

  ```sh 
  $ mkdir /tmp/java_projects/
  $ cd /tmp/java_projects/
  $ git clone https://github.com/JakeWharton/ActionBarSherlock.git
  ```

Now you can train the topic model on the java projects in /tmp/java_projects/

  ```sh 
  $ java -cp tassal/target/tassal-1.1-SNAPSHOT.jar codesum.lm.tui.TrainTopicModel   
   -d /tmp/java_projects/  -w /tmp/ -i 100 
  ```

This trains the topic model for 100 iterations and outputs the model to /tmp/. We can then fold a specific file 

  ```sh 
  $ java -cp tassal/target/tassal-1.1-SNAPSHOT.jar codesum.lm.tui.FoldSourceFile     
   -w /tmp/  -c 50 -p ActionBarSherlock 
   -f /tmp/java_projects/ActionBarSherlock/actionbarsherlock/src/com/actionbarsherlock/app/SherlockFragment.java 
   -o /tmp/SherlockFragmentFolded.java 
  ```

which will output the folded file to /tmp/SherlockFragmentFolded.java. 

Summarizing a Project
---------------------

TASSAL is also able to summmarize an entire project by finding the top source files (and therefore classes) representative of that project. These top project files can then be autofolded using TASSAL as above (see [Autofolding a source file](#autofolding-a-source-file)).

*codesum.lm.tui.ListSalientFiles* lists the most representative source files for a given project. It has the following command line options:

* **-s** &nbsp;  working directory where the topic model creates necessary files (same as above)
* **-d** &nbsp;  directory containing java projects
* **-p** &nbsp;  project to summarize
* **-c** &nbsp;  desired compression ratio (% of project files to list)
* **-b** &nbsp;  (optional)  background topic to back off to (0-2, default=2) 
* **-o** &nbsp;  (optional)  where to save the salient files
* **-i** &nbsp;  (optional)  whether to ignore unit test files (default=true) 

Note that this requires the topic model to first be trained on the given project (see [Training the source code topic model](#training-the-source-code-topic-model) above). 

Bugs
----

Please report any bugs using GitHub's issue tracker.

License
-------

This tool is released under the new BSD license.
