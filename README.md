# Facebook-Simulator

This system implements few APIs related to user profiles, managing friends lists, user posts, friends posts.
It is written in scala using akka and Spray libraries.
The data encryption is done using both AES and RSA libraries.

Server.scala starts a virtual FB like server, generates users and initial setup. Then it starts listening to REST requests from port 8080.
Using client.scala communicates to server. All the data used in communication is encrypted using AES libraries. Respective AES keys are encrypted using public keys of users who can have access to the data.
