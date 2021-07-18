const base64Decode = (x) => { return Uint8Array.from(atob(x), c => c.charCodeAt(0)) }
const base64Encode = (x) => { return btoa(String.fromCharCode(...new Uint8Array(x))); }

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