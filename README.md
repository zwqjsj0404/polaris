Polaris Code Search
===================

A code search engine for Java

Prestiques
---------
* JDK 1.6+
* Maven 2+
* Git
* GNU Toolchain (gcc, g++, make, automake, autoconf, libtool...)
* Protocol Buffers 2.40+

Build Java Parser
-----------------
		$ git clone https://github.com/matozoid/javaparser.git
		$ cd javaparser
		$ mvn install
		$ cd ..

Build Apache Crunch
-------------------
		$ git clone https://git-wip-us.apache.org/repos/asf/crunch.git
		$ cd crunch
		$ mvn install -Phadoop-2 -DskipTests
		$ cd ..

Build Polaris
-------------
Go to Polaris project root and run:

		$ git clone https://github.com/stepinto/polaris.git
		$ cd polaris
		$ mvn package

Run
---
1. Build index

		$ ./polaris index path-to-project1 path-to-project2 ... 

	The index files will be stored in "index/".

2. Start searcher

		$ ./polaris devserver

    Navigate to http://localhost:8080.
