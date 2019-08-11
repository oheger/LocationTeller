# LocationTeller

LocationTeller is an Android application that was created to support still
mobile people suffering from dementia, and their relatives. The main 
functionality is to send updates about the current location to a server
periodically. The locations can then be displayed on a map view.

So when a disoriented person goes out for a walk, he or she can take a smart
phone running this application. Other people can track the current position of
this person.

There are many applications available that support tracking of the current
location. For the intended use case the tracking requirements are a bit special
though. Rather than being highly accurate, it is more important that the app is
always active, saves energy, and sends location information in a reliable
manner. People suffering from dementia might leave their houses at any time
without telling their relatives. So a typical usage scenario could be that the
smart phone is put into a pocket of the clothing of the demented person. When
he or she is gone it is then possible to query the current position. For this
to work the application must be running continuously without draining the
phone's battery.

To make this possible, the tracking behavior can be configured with different
parameters:
* The tracking interval: This is the interval in which location updates are
  sent. It is specified in minutes because for the intended use case this
  granularity is sufficient. (You do not need to know the exact position in
  real time; it is enough if you know where the person was a few minutes ago.)
* If a location request yields the same position than the previous request, the
  application assumes that tracking is in inactive state. This means that the
  person is no longer walking around. The application then switches in a 
  stand-by mode by successively increasing the tracking interval by a specific
  value.
* The maximum tracking interval: If this value is reached, the interval between
  two location requests is no longer increased. The application stays at this
  interval until a change in the location is detected again. Then it switches
  back to the tracking interval.
  
By fine-tuning these parameters, the tracking behavior can be customized to 
your own specific needs. The greater the maximum tracking interval, the less
energy is consumed by the application when it is inactive - but the longer it
takes to become active again and send an up-to-date location. So these 
parameters have to be balanced carefully.

Another difference between LocationTeller and typical tracking applications 
lies in the fact that locations retrieved are directly uploaded to a server,
from where they can be accessed by the receiver part of the application.
Other tracking applications usually record a complete _track_ which can then be
exported manually.

The server to which locations are uploaded can be configured. Currently servers
offering the WebDav protocol with basic authentication are supported.
