#!/bin/bash
mvn install:install-file -DgroupId="atg" -DartifactId="das" -Dversion="10.0.1" -Dpackaging="jar"  -Dfile="${ATG_HOME}/DAS/lib/classes.jar" -DgeneratePom="true" -e
mvn install:install-file -DgroupId="atg" -DartifactId="bcc" -Dversion="10.0.1" -Dpackaging="jar"  -Dfile="${ATG_HOME}/BCC/lib/classes.jar" -DgeneratePom="true" -e
mvn install:install-file -DgroupId="atg" -DartifactId="dps" -Dversion="10.0.1" -Dpackaging="jar"  -Dfile="${ATG_HOME}/DPS/lib/classes.jar" -DgeneratePom="true" -e
mvn install:install-file -DgroupId="atg" -DartifactId="dcs" -Dversion="10.0.1" -Dpackaging="jar"  -Dfile="${ATG_HOME}/DCS/lib/classes.jar" -DgeneratePom="true" -e
mvn install:install-file -DgroupId="atg" -DartifactId="webui" -Dversion="10.0.1" -Dpackaging="jar"  -Dfile="${ATG_HOME}/WebUI/lib/classes.jar" -DgeneratePom="true" -e
mvn install:install-file -DgroupId="atg" -DartifactId="epub" -Dversion="10.0.1" -Dpackaging="jar" -Dfile="${ATG_HOME}/PublishingWebAgent/lib/classes.jar" -DgeneratePom="true" -e



