let chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

// https://github.com/fido-alliance/webauthn-demo/blob/master/static/js/base64url-arraybuffer.js
// Use a lookup table to find the index.
let lookup = new Uint8Array(256);
for (let i = 0; i < chars.length; i++) {
    lookup[chars.charCodeAt(i)] = i;
}

let decode = function(base64string) {
    let bufferLength = base64string.length * 0.75,
        len = base64string.length, i, p = 0,
        encoded1, encoded2, encoded3, encoded4;

    let bytes = new Uint8Array(bufferLength);

    for (i = 0; i < len; i+=4) {
        encoded1 = lookup[base64string.charCodeAt(i)];
        encoded2 = lookup[base64string.charCodeAt(i+1)];
        encoded3 = lookup[base64string.charCodeAt(i+2)];
        encoded4 = lookup[base64string.charCodeAt(i+3)];

        bytes[p++] = (encoded1 << 2) | (encoded2 >> 4);
        bytes[p++] = ((encoded2 & 15) << 4) | (encoded3 >> 2);
        bytes[p++] = ((encoded3 & 3) << 6) | (encoded4 & 63);
    }

    return bytes.buffer
};

let encode = function(arraybuffer) {
    let bytes = new Uint8Array(arraybuffer),
        i, len = bytes.length, base64url = '';

    for (i = 0; i < len; i+=3) {
        base64url += chars[bytes[i] >> 2];
        base64url += chars[((bytes[i] & 3) << 4) | (bytes[i + 1] >> 4)];
        base64url += chars[((bytes[i + 1] & 15) << 2) | (bytes[i + 2] >> 6)];
        base64url += chars[bytes[i + 2] & 63];
    }

    if ((len % 3) === 2) {
        base64url = base64url.substring(0, base64url.length - 1);
    } else if (len % 3 === 1) {
        base64url = base64url.substring(0, base64url.length - 2);
    }

    return base64url;
};
const getAuthPayload = async (resident) => {
    let r;
    if (resident) {
        r = 'yes';
    } else {
        r = 'no';
    }
    const res = await fetch(`/authn/register?resident=${r}`);
    if (res.status === 403) {
        document.getElementById("webauthn_status").textContent = "Could not generate payload because you are no longer logged in";
        return;
    }

    if (!res.ok) {
        document.getElementById("webauthn_status").textContent = "Could not generate key registration ceremony"
        return
    }

    const body = await res.json()

    body.challenge = decode(body.challenge);
    body.user.id = decode(body.user.id);

    body.excludeCredentials = body.excludeCredentials.map((curr) => {
        return {
            id: decode(curr.id),
            type: curr.type
        }
    })


    let cred;
    try {
        cred = await navigator.credentials.create({
            publicKey: body
        })
    } catch (e) {
        console.log(e)
        document.getElementById("webauthn_status").textContent = "error. is this key already registered? Did you cancel?"
        return;
    }

    const payload = {
        id: cred.id,
        type: cred.type,
        response: {
            attestationObject: encode(cred.response.attestationObject),
            clientDataJSON: encode(cred.response.clientDataJSON)
        },
        clientExtensionResults: {}
    }


    console.log(payload)
    console.log(JSON.stringify(payload))

    const finish = await fetch('/authn/register', {
        method: 'POST',
        body: JSON.stringify(payload)
    })

    if (finish.status === 204) {
        document.getElementById("status").textContent = "registered"
        location.reload()
    } else {
        document.getElementById("status").textContent = "error on register"
    }

    console.log(finish)
}

const doAuth = async () => {
    const res = await fetch('/authn/assert');
    if (!res.ok) {
        return;
    }
    const body = (await res.json()).publicKeyCredentialRequestOptions

    body.challenge = decode(body.challenge);
    body.allowCredentials = body.allowCredentials.map((curr) => {
        return {
            id: decode(curr.id),
            type: curr.type
        }
    })

    const cred = await navigator.credentials.get({
        publicKey: body

    })

    const payload = {
        id: cred.id,
        type: cred.type,
        response: {
            authenticatorData: encode(cred.response.authenticatorData),
            clientDataJSON: encode(cred.response.clientDataJSON),
            signature: encode(cred.response.signature)
        },
        clientExtensionResults: {}
    }


    const finish = await fetch('/authn/assert', {
        method: 'POST',
        body: JSON.stringify(payload)
    })

    if (finish.status === 200) {
        const responseBody = await finish.json()
        const key = responseBody.ticket;
        const dest = "/authn/complete?ticket=" + key;
        window.location.href = dest;
    } else {
        document.getElementById("status").textContent = "error on login"
    }

    console.log(finish)
}

const unenroll = async () => {
    await fetch('/authn', {
        method: 'DELETE'
    })

    location.reload()
}

const residentEnroll = async () => {
    const res = await fetch('/authn/assert_resident');
    if (!res.ok) {
        return;
    }
    const body = (await res.json()).publicKeyCredentialRequestOptions

    body.challenge = decode(body.challenge);

    const key = res.headers.get("X-Assert-Key")

    const cred = await navigator.credentials.get({
        publicKey: body

    })

    const payload = {
        id: cred.id,
        type: cred.type,
        response: {
            authenticatorData: encode(cred.response.authenticatorData),
            clientDataJSON: encode(cred.response.clientDataJSON),
            signature: encode(cred.response.signature)
        },
        clientExtensionResults: {}
    }


    const finish = await fetch(`/authn/assert_resident?key=${key}`, {
        method: 'POST',
        body: JSON.stringify(payload)
    })

    if (!finish.ok) {
        document.getElementById("webauthn_status").textContent = "Error Authenticating"
        return
    }

    const finishBody = await finish.json()

    location.href = `/authn/complete?ticket=${finishBody.ticket}`
}