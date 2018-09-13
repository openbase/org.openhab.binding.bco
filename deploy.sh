#!/bin/bash

#echo '[INFO] try to identify eclipse smarthome homefolder location...'


# Load openhab directory paths if available and use those for
if [ -r /etc/profile.d/openhab2.sh ]; then
  . /etc/profile.d/openhab2.sh
elif [ -r /etc/default/openhab2 ]; then
  . /etc/default/openhab2
fi

if [ ! -d "$ECLIPSE_SMARTHOME_HOME" ]; then
    ECLIPSE_SMARTHOME_HOME=$OPENHAB_HOME
fi

if [ ! -d "$ECLIPSE_SMARTHOME_HOME" ]; then
    echo "[ERROR] eclipse smarthome homefolder [$ECLIPSE_SMARTHOME_HOME] not found!"
    echo '[INFO] please refer the home folder by calling "mvn install -Dorg.eclipse.smarthome.home=PATH"'
    echo '[INFO] or globally define the home folder via the system variables: $ECLIPSE_SMARTHOME_HOME or $OPENHAB_HOME'
    echo [WARN] skip binding deploy process...
    exit ;
fi

ADDONS_HOME=$ECLIPSE_SMARTHOME_HOME/addons

#echo [INFO] deploy binding into $ECLIPSE_SMARTHOME_HOME ...
if [ ! -w $ADDONS_HOME ] ; then
    echo "No permissions to deploy binding into $ADDONS_HOME"
    echo "Sudo password needed to adjust permissions for user $USER"
    sudo chgrp $USER $ADDONS_HOME
    sudo chmod g+rwx $ADDONS_HOME
fi

rm -f $ADDONS_HOME/org.eclipse.smarthome.binding.bco-*.jar
scp -r target/*jar $ADDONS_HOME

