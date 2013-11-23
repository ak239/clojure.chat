//some globals
var conn;                       
var state;    

const INIT = 0;
const CONNECTED = 1;
const LOGIN_WAIT = 2;
const LOGIN_OK = 3;
const LOGOUT = 4;

const FETCH_INTERVAL  = 1000;

//entry point                 
(function () {

	var fetch_id;
	var $i = $('#i');
	state = INIT;
	
//	conn = new WebSocket("ws://" + window.location.host + "/ws");

	conn = new WebSocket("ws://178.162.76.108:9001/ws");
	
	conn.onerror = function () {
		$('div').hide();
		document.getElementById('wrapper').style.display = "block";
		document.getElementById('error').style.display = "block";
		console.log(arguments);
		logout();
	};
	
	conn.onconnect = function () {
		state = CONNECTED;
	}

  conn.onmessage = handleMessage; 
	
	$('#loginbutton').click(login);

	function handleMessage (e) {
		console.log(e);
		console.log(STATE === LOGIN_WAIT);
		var msg = JSON.parse(e.data);
		console.log(e.data);
		
		if (STATE == LOGIN_WAIT) {
				STATE = LOGIN_OK;
				//window.onunload = logout();
				window.onunload = console.log("unload");
				document.getElementById('chatwindow').style.display = "block";
				document.getElementById('login').style.display = "none";
				fetch_id = start_fetch();
		} else if (STATE == LOGIN_OK) {
				//console.log(msg);
				if ((msg.status == "ok") && (msg.data != undefined)) {
					var msgs = msg.data;
					for (var i = 0; i < msgs.length; i++) {
						add_msg(msgs[i]);
					}
				}
		}
		
	}
	
	function login () {
		STATE = LOGIN_WAIT;
		name = $('#name').val();
		conn.send(JSON.stringify({command: "login", data: name, flags: "", version: "1" }));
	}
	
	function logout () {
		console.log("logout");
		state  = LOGOUT;
		stop_fetch(fetch_id);	
		conn.send(JSON.stringify({command: "logout", data: "", flags: "", version: "1" }));
	}

  function add_msg (msg) {
	var date = new Date(msg.time*1000); // multiplied by 1000 so that the argument is in milliseconds, not seconds
	var formattedTime = date.getHours() + ':' + date.getMinutes() + ':' + date.getSeconds();
    $('#history').append('<li>' + formattedTime + " <b>" + msg.author + "</b> " + msg.msg +'</li>');
	$("#history-wrap").scrollTop($("#history-wrap")[0].scrollHeight);
  }

  function send_to_server () {
    var msg = $.trim($i.val());
		
    if(msg) {
			conn.send(JSON.stringify({command: "send", data: msg, flags: "", version: "1" }));
      $i.val('');
    }
	return false;
  }

  function start_fetch () {
	  return setInterval(function () {
		var msg = {command: "fetch", data: "", flags: "", version: "1"};
		console.log('fetch', msg);
		conn.send(JSON.stringify(msg));
	  }, FETCH_INTERVAL);	
	}

	function stop_fetch(id) {
		clearInterval(id);
	}
  
  $('#send').click(send_to_server);
  $i.focus().keyup(function (e) {
    if(e.which == 13) {        // enter
      send_to_server();
    }
  });
})();


function start_robot (name) {
  var id = 0;

  setInterval(function () {
  
    var msg = {command: "send", data: "mesg#" + id, flags: "", version: "1"};
    console.log('sending...........', msg);
    conn.send(JSON.stringify(msg));
    id += 1;
  }, 1000);
}