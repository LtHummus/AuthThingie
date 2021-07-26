function websocketScheme() {
    if (window.location.href.startsWith("https")) {
        return "wss";
    } else {
        return "ws";
    }
}

function initLogin(socketUrl) {
    const button = document.getElementById("duo_submit");
    button.disabled = true;
    const deviceId = document.getElementById("device").value;

    const params = new URLSearchParams();
    params.append('device', deviceId);

    const url = "/duo/push?" + params.toString();
    document.getElementById("duo_spinner").style.display = '';
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

    const fixedSocketUrl = websocketScheme() + socketUrl.substring(2);

    let socket = new WebSocket(fixedSocketUrl + "?" + params.toString());

    socket.onopen = (e) => {
        console.log('connected to socket');
        socket.send("check");
    };

    socket.onmessage = (e) => {
        console.log(e.data);
        const data = JSON.parse(e.data);
        console.log(data);
        document.getElementById("duo_status").innerText = data.status_msg;
        if (data.result !== "waiting") {
            document.getElementById("duo_spinner").style.display = 'none';
            socket.send("die");

            const dest = "/ticket?ticket=" + data.ticket;
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
