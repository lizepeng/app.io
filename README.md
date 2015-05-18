# App.io

This application shows how to build scalable web application with Play Framework, Cassandra, Elastic Search, Bootstrap and AngularJS. By including cassandra and elasticsearch, app.io achieved non-blocking from head to toe.

In this application, I managed to combine hottest projects hosting on github.com. By starting from app.io, it could be easier to create a new scalable web application.

Those awesome projects app.io build on are shown as below:

- [Play Framework](https://github.com/playframework/playframework)
- [phantom (Scala driver for Cassandra)](https://github.com/websudos/phantom)
- [elastic4s (Scala client for Elastic Search)](https://github.com/sksamuel/elastic4s)
- [Bootstrap](https://github.com/twbs/bootstrap)
- [Font Awesome](https://github.com/FortAwesome/Font-Awesome)
- [AngularJS](https://github.com/angular/angular.js)
- [Underscore](https://github.com/jashkenas/underscore)
- [FlowJs](https://github.com/flowjs/flow.js)

## Main Features

- REST API for communication between WebUI and server
- Distributed File System built on cassandra
- Responsive WebUI for mobile, tablet PC and so on
- Searching entity by using elasticsearch
- User sign up / sign in / sign out
- User based authentication check
- Group based permission check
- Rate Limiting against api abuse
- Mail template for sending one email to multi customers.
- MultiAccounts mailer plugin for play framework

## Usage

### Start Cassandra

At first you should start Cassandra, assuming that you're using Mac OS, then

```sh
$ brew install cassandra
$ cassandra
```

### Start Elastic Search

Second, install and launch elasticsearch

```sh
$ brew install elasticsearch
$ elasticsearch
```

### Start the Application

As with every Play 2 application, just use `activator run`.

## License

This software is licensed under the Apache 2 license.