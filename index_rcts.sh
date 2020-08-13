#!/bin/bash

COLL=rcts/
INDEX=rct_index/

cat > index.rcts.properties << EOF1

coll=$COLL
index=$INDEX
stopfile=stop.txt
sax.parser=generic
sax.docstart=clinical_study
sax.docid=nct_id
sax.content_tags=brief_summary,detailed_description,study_design_info,intervention,eligibility,clinical_results

EOF1

mvn exec:java@index -Dexec.args="index.rcts.properties"


