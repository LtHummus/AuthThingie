function initLogin(socketUrl) {
    const deviceId = document.getElementById("device").value;

    const params = new URLSearchParams();
    params.append('device', deviceId);

    const url = "/duoPush?" + params.toString();
    document.getElementById("spinner").style.display = '';
    fetch(url).then(res => {
        return res.json();
    }).then(payload => {
        console.log(payload);
        getStatus(payload.txId, socketUrl);
    });
}


function getStatus(txId, socketUrl) {
    const ourUrl = new URL(window.location.href);

    const params = new URLSearchParams();
    params.append('txId', txId);
    params.append('redirectUrl', ourUrl.searchParams.get('redirect'));

    let socket = new WebSocket(socketUrl + "?" + params.toString());

    socket.onopen = (e) => {
        console.log('connected to socket');
        socket.send("check");
    };

    socket.onmessage = (e) => {
        console.log(e.data);
        const data = JSON.parse(e.data);
        console.log(data);
        document.getElementById("status").innerText = data.status_msg;
        if (data.result !== "waiting") {
            document.getElementById("spinner").style.display = 'none';
            socket.send("die");

            const b64 = btoa(e.data).replace(/\+/g, '-').replace(/\//g, '_').replace(/\=+$/, '');
            const dest = "/duoPostCheck?key=" + b64;
            window.location.href = dest;
        } else {
            socket.send("check");
        }
    }

    socket.onclose = (e) => {
        if (e.wasClean) {
            console.log('socket closed clean');
        } else {
            console.log('socket close error');
        }
    };
}
