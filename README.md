# AuthThingie

AuthThingie (names are hard, ok?) is a simple web server that can be used with Traefik's Forward Authentication setting to provide SSO-ish access to a website. I wrote this because I have a home server running a bunch of docker-containers and frontend by Traefik. Each service originally used Traefik's Basic-Auth plugin, which worked fine, except you had to authenticate yourself to each site separately. I found other applications for doing a SSO-approach, but I didn't want to set up something heavy like [Keycloak](https://www.keycloak.org/) or [Authelia](https://www.authelia.com/). And I didn't want to have to rely on a third party to authenticate (so [traefik-forward-auth](https://github.com/thomseddon/traefik-forward-auth) was also out). I also thought it might be a fun thing to write.

**AuthThingie is still a work in progress! Be careful!**

## Configuration

Everything is handled in a `auththingie_config.conf` file. Here's an example:

```hocon
play {
  filters.hosts {
    allowed = [".example.com", "auth:9000", "localhost:9000"]
  }

  http {
    secret.key = "SECRET" # should be replaced with a randomly generated, long string

    session {
      domain = "example.com"

      jwt {
        signatureAlgorithm = "HS256"
      }
    }
  }
}



"rules": [
  {
    "name": "/css* on test.example.com",
    "pathPattern": "/css*",
    "hostPattern": "test.example.com",
    "public": true,
    "permittedRoles": []
  },
  {
      "name": "/js* on test.example.com",
      "pathPattern": "/js*",
      "hostPattern": "test.example.com",
      "public": true,
      "permittedRoles": []
  },
  {
      "name": "/animals* on test.example.com",
      "pathPattern": "/animals*",
      "hostPattern": "test.example.com",
      "public": false,
      "permittedRoles": ["animal_role"]
  },
  {
    "name": "test.example.com root",
    "hostPattern": "test.example.com",
    "pathPattern": "/",
    "public": true,
    "permittedRoles": []
  }
]

"users": [
  {
    "htpasswdLine": "ben:$2y$05$WvtSdzLmwYqZqUe/EdLt1uG250dUmHAdQ4nKEDP.J5KRM2u3JbTCS",
    "admin": true,
    "roles": []
  },
  {
    "htpasswdLine": "dog:$2y$05$/WME1Gi5RRG/or8To0BjQewJ6lg0z/IyaRjLyhW8yx0ygVwMoJjGO",
    "admin": false,
    "roles": ["animal_role"]
  }
]

"auth_site_url": "http://auth.example.com"
``` 

Everything should be more or less self-explanatory at this point. Essentially, you have your users and your rules. Every user needs an `htpasswdLine` (essentially the output generated from `htpasswd -nB <username>`), a flag indicating if they are an admin, and a list of roles that the user has. Note you will need to set some things in the `play` section (TODO: make it so you don't have to do that): notably the domain for the session, the secret key (please don't use the one in the sample file; just any long, randomly generated string will do), and the hostnames the server will live under (`auth.example.com` and perhaps `auth:9000` so Traefik can talk to it internally). 

Each rule has a `name`, a list of `permittedRoles` (which can be empty for admin only), a `public` flag (public means everyone is allowed, logged in or not), and then at least one of `hostPattern`, `pathPattern`, or `protocolPattern` to match against. Any of those three not specified means "ANY". Everything is specified using simple wildcards: `?` matches a single character, and `*` matches many characters (on the todo list is a rule tester). Any path that matches no rules is implicitly "admin-only." Admin users implicitly have access to everything.

`auth_site_url` should be the public URL of the authentication site. Note you will also have to add the proper domains to your `play.filters.hosts` section of the config file. You should add all hostnames that this site can potentially be reached from (both inside your docker network and outside). Alternatively, you can add `play.filters.enabled -= play.filters.hosts.AllowedHostsFilter` to the config file to disable the hosts filter completely. You will need to set your domain for the `play.http.session.domain` entry.

When logging in is needed, the user will automatically be redirected to a login page. Once logged in, the user will be redirected back to where they were going (if they have permission). Additionally, credentials can be passed in using basic-auth (via the `Authorization` header). This is useful if you have an app that interacts with a service behind your authentication but can't handle the redirects properly.

## Deployment

This is meant to be used with traefik's forward authentication plugin. If you just want to get up and going and see how it works, I have included a `docker-compose.yaml` file that has an example service running at `test.example.com` and the authentication running at `auth.example.com`. You will need to add entries for both to your HOSTS file to get the domains to work. Add something like

```
127.0.0.1 test.example.com
127.0.0.1 auth.example.com
```

to your computer's hosts file, then run `./build.sh` to boot everything (you will need `sbt` installed to compile the service). Run `docker-compose down` to tear everything down when you are done. In this example config, the root of `test.example.com` is allowed by everyone. Going anywhere else should redirect you to the login page. The username/password for the admin user in the sample config is username `ben` and password is `abc`. There is also the account `dog` with the password `woof` that can only access the animal section of the sample website. (See https://github.com/LtHummus/SampleSite for more info on the sample site).

### Deployment for real

Create a `auththingie_config.conf` file somewhere on your file system. Create the docker container from the image and mount that config file in the container. Set the environment variable `AUTHTHINGIE_CONFIG_FILE_PATH` to point to where the config file lives in the container. Set your Traefik config to point forward authentication to `/auth` on the server See the included `docker-compose.yaml` file for a complete example. Note the `traefik.frontend.auth.forward.address: "http://auth:9000/auth"` label on the sample website.

## You are likely to be eaten by a grue
**Remember! This is a work in progress!**
 
This is like 95% experimental right now. There are definitely some things that are rough around the edges. You can run this if you want (let me know how it goes!), but this is definitely an "at your own risk" situation.

## TODO List

In no particular order...

* Make it not look terrible
* Perhaps read config from a database?
* 2FA Support