#!/bin/bash

set -x

for f in mps/*; do
    awk -i inplace '!seen[$0]++' $f
    perl -pi -e "s/LO[\s]+bnd[\s]+sum[\s]+.+/LO bnd sum ${1}/g" $f
done

