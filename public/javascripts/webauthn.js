const base64Decode = (x) => {
    return Uint8Array.from(atob(x), c => c.charCodeAt(0))
}
const base64Encode = (x) => {
    return btoa(String.fromCharCode(...new Uint8Array(x)));
}

const beginRegistration = async (residentKey) => {
    const registrationResponse = await fetch(`/authn/register?residentKey=${residentKey}`)
    if (!registrationResponse.ok) {
        console.log("could not generate registration payload")
    }
    const responseJson = await registrationResponse.json()
    console.log(responseJson)

    const registrationInfo = {
        challenge: base64Decode(responseJson.registrationPayload.challenge),
        rp: responseJson.registrationPayload.rp,
        authenticatorSelection: {
            requiresResidentKey: residentKey
        },
        user: {
            id: base64Decode(responseJson.registrationPayload.userHandle),
            name: responseJson.registrationPayload.username,
            displayName: responseJson.registrationPayload.username
        },
        excludeCredentials: responseJson.registrationPayload.registeredKeys.map((x) => {
            return {
                type: 'public-key',
                id: base64Decode(x)
            }
        }),
        // EC + SHA256
        pubKeyCredParams: [{alg: -7, type: "public-key"}],
        timeout: 60000,
        attestation: "none"
    }

    console.log(registrationInfo)

    const credential = await navigator.credentials.create({
        publicKey: registrationInfo,
    })

    console.log(credential)

    const response = {
        id: responseJson.registrationId,
        keyId: base64Encode(credential.rawId),
        attestationObject: base64Encode(credential.response.attestationObject),
        clientData: base64Encode(credential.response.clientDataJSON),
        kind: credential.type
    }

    const completionResponse = await fetch('/authn/register', {
        method: 'POST',
        body: JSON.stringify(response),
        headers: {
            'Content-Type': 'application/json'
        }
    })

    console.log(completionResponse)

}

const beginAuthentication = async () => {
    const authenticationResponse = await fetch(`/authn/validate`)
    if (!authenticationResponse.ok) {
        console.log("could not generate registration payload")
    }
    const responseJson = await authenticationResponse.json()
    console.log(responseJson)

    const registrationInfo = {
        challenge: base64Decode(responseJson.authenticationPayload.challenge),
        rp: responseJson.authenticationPayload.rp,
        userVerification: "discouraged"
    }

    if (responseJson.authenticationPayload.allowedKeys) {
        registrationInfo.allowCredentials = responseJson.authenticationPayload.allowedKeys.map((x) => {
            return {
                type: 'public-key',
                id: base64Decode(x)
            }
        })
    }

    console.log(registrationInfo)

    const credential = await navigator.credentials.get({
        publicKey: registrationInfo
    })

    console.log(credential)

    const packedResponse = {
        id: responseJson.authId,
        keyId: base64Encode(credential.rawId),
        authenticatorData: base64Encode(credential.response.authenticatorData),
        clientData: base64Encode(credential.response.clientDataJSON),
        signature: base64Encode(credential.response.signature)
    }

    const completionResponse = await fetch('/authn/validate', {
        method: 'POST',
        body: JSON.stringify(packedResponse),
        headers: {
            'Content-Type': 'application/json'
        }
    })

    console.log(completionResponse)
}