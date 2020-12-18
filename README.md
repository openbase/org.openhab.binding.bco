# org.eclipse.smarthome.binding.bco

This is the official bco binding of openhab.
Once added, it makes all BCO Units avalable to openhab. Therefore, BCO Units can be controlled via the sitemaps or other openhab UIs.

This binding requires a running instance of a bco openhab device manager (bco-manager-device-openhab) to work correctly.

# How to enable debug logging

To get a better overview about what is going on within the binding during development, this howto explains how the internal debug log of the bco binding can be accessed via the [karaf console|https://www.openhab.org/docs/administration/console.html].

Connect to the host where openhab is running on.
```
ssh myopenhabhost
```

Ony directy form this host we are able to establish a connnection to the openhab console.
```
ssh -p 8101 openhab@localhost
```
accept key during first login. In case of a timeout, retry again.
```
The authenticity of host '[localhost]:8101 ([127.0.0.1]:8101)' can't be established.
RSA key fingerprint is ea:db:38:d1:fb:f6:fd:8b:be:3a:74:40:24:b9:82:a5.
Are you sure you want to continue connecting (yes/no)? yes
```
Default openhab console password: ```habopen```

## enable debug log of the bco binding
Within the karaf console, the log can be enabled via:
```log:set DEBUG org.openbase```

Then you can print the log via:
```log:tail```
