# TraefikCop

TraefikCop is a simple web server that can be used with Traefik's Forward Authentication setting to provide SSO-ish access to a website. I wrote this because I have a home server running a bunch of docker-containers and frontend by Traefik. Each service originally used Traefik's Basic-Auth plugin, which worked fine, except you had to authenticate yourself to each site separately. I found other applications for doing a SSO-approach, but I didn't want to set up something heavy like [Keycloak](https://www.keycloak.org/) or [Authelia](https://www.authelia.com/). And I didn't want to have to rely on a third party to authenticate (so [traefik-forward-auth](https://github.com/thomseddon/traefik-forward-auth) was also out). I also thought it might be a fun thing to write.

**TraefikCop is still a work in progress! Be careful!**

## Configuration

Everything is handled in a `traefikcop_config.conf` file. Here's an example:

```hocon
play {
  filters.hosts {
    allowed = [".example.com", "auth:9000", "localhost:9000"]
  }

  http {
    secret.key = "9IoC0znSqS4oaSd7KaR3yeoXy4yET3XD"

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
    "name": "/bin* on everything",
    "path": "\\/bin.*",
    "public": true,
    "permittedRoles": ["a"]
  },
  {
    "name": "test.example.com root",
    "server": "test.example.com",
    "path": "/",
    "public": true,
    "permittedRoles": ["a", "b"]
  }
]

"users": [
  {
    "htpasswdLine": "ben:$2y$05$WvtSdzLmwYqZqUe/EdLt1uG250dUmHAdQ4nKEDP.J5KRM2u3JbTCS",
    "admin": true,
    "roles": ["c"]
  }
]

"auth_url": "http://auth.example.com"
"realm": "example.com"
``` 

Everything should be more or less self-explanatory at this point. Essentially, you have your users and your rules. Every user needs an `htpasswdLine` (essentially the output generated from `htpasswd -nB <username>`), a flag indicating if they are an admin, and a list of roles that the user has.

Each rule has a `name`, a list of `permittedRoles` (which can be empty for admin only), a `public` flag (public means everyone is allowed, logged in or not), and then at least one of `server`, `path`, or `protocol` to match against. Any of those three not specified means "ANY". Everything is specified using Java regular expression syntax.

## Hosting

This is meant to be used with traefik's forward authentication plugin. If you just want to get up and going and see how it works, I have included a `docker-compose.yaml` file that has an example service running at `test.example.com` and the authentication running at `auth.example.com`. You will need to add entries for both to your HOSTS file to get the domains to work. Add something like

```
127.0.0.1 test.example.com
127.0.0.1 auth.example.com
```

to your computer's hosts file, then run `./build.sh` to boot everything (you will need `sbt` installed to compile the service). Run `docker-compose down` to tear everything down when you are done. In this example config, the root of `test.example.com` is allowed by everyone and `/bin` is allowed on any server. Going anywhere else should redirect you to the login page. The username/password for the sample config is username `ben` and password is `abc`.

**Remember! This is a work in progress!***
 
 *AKA if it screws everything up, then don't blame me......