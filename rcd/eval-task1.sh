#!/bin/bash

cd ..

PREDRES=rcd/pred_rel.txt
EQUIV_QRIES=rcd/equiv.txt

mvn exec:java@rcd_task1_eval -Dexec.args="$PREDRES $EQUIV_QRIES"

cd -

