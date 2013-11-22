
//some globals
var conn;                       
var state;    

const INIT = 0;
const CONNECTED = 1;
const LOGIN_WAIT = 2;
const LOGIN_OK = 3;

//entry point                 
(function () {
  var $i = $('#i');

  var last_id = 0;

	state = INIT;
	
  conn = new WebSocket("ws://192.168.1.20:9001/ws");
	
	conn.onerror = function () {
		$('div').hide();
		document.getElementById('wrapper').style.display = "block";
		document.getElementById('error').style.display = "block";
    console.log(arguments);
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
				last_id = msg.last_id;
				STATE = LOGIN_OK;
				//window.onunload = logout();
				document.getElementById('chatwindow').style.display = "block";
				document.getElementById('login').style.display = "none";
		} else if (STATE == LOGIN_OK) {
				console.log(msg.data);
				last_id = msg.last_id;
				add_msg(msg.data);
		}
		
	}
	
	function login () {
		STATE = LOGIN_WAIT;
		name = $('#name').val();
		conn.send(JSON.stringify({command: "login", data: name, flags: "", version: "1" }));
	}

	function fetch() {
	    conn.send(JSON.stringify({command: "fetch", data: "", flags: "", version: "1" }));
	}
	
	function logout () {
		console.log("logout");
		//conn.send(JSON.stringify({command: "logout", data: "", flags: "", version: "1" }));
	}

  function add_msg (msg) {
    $('#history').append('<li>' + msg +'</li>');
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