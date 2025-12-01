setTimeout(function() {
  console.log('AWS CONNECT: Setting up event listeners...');

  // Check if connect global object exists
  if (typeof connect !== 'undefined' && connect.contact) {
    console.log('AWS CONNECT: connect object found, subscribing to events...');

    // Global contact reference for Java button control
    window.currentContact = null;

    // Helper function to notify Java about incoming call
    function notifyIncomingCall(contact) {
      var contactId = contact.getContactId();
      var attr = contact.getAttributes();
      var queue = contact.getQueue();
      var queueName = queue ? queue.name : 'Unknown';
      var conn = contact.getInitialConnection();
      var phoneNumber = 'Unknown';
      try {
        if (conn && conn.getEndpoint()) phoneNumber = conn.getEndpoint().phoneNumber || 'Unknown';
      } catch(e) { console.log('AWS CONNECT: Error getting phone number: ' + e); }
      var data = JSON.stringify({contactId: contactId, phoneNumber: phoneNumber, queue: queueName, type: 'incoming', attribute: attr});
      console.log('AWS CONNECT: Notifying Java - INCOMING_CALL: ' + data);
      window.cefQuery({request: 'INCOMING_CALL:' + data});
    }

    // Contact (call) events
    connect.contact(function(contact) {
      var contactId = contact.getContactId();
      var contactState = contact.getStatus().type;
      var contactType = contact.getType();
      console.log('AWS CONNECT: ========== NEW CONTACT ==========');
      console.log('AWS CONNECT: Contact ID: ' + contactId);
      console.log('AWS CONNECT: Contact State: ' + contactState);
      console.log('AWS CONNECT: Contact Type: ' + contactType);
      console.log('AWS CONNECT: ================================');
      window.currentContact = contact;

      var queue = contact.getQueue();
      var queueName = queue ? queue.name : 'Unknown';

      // Check if this is already an incoming call (state check)
      if (contactState === 'incoming' || contactState === 'connecting') {
        console.log('AWS CONNECT: Contact arrived in ' + contactState + ' state - treating as INCOMING');
        notifyIncomingCall(contact);
      }

      // Incoming call event
      contact.onIncoming(function(contact) {
        console.log('AWS CONNECT: onIncoming event fired!');
        window.currentContact = contact;
        notifyIncomingCall(contact);
      });

      // Refresh event - fires when contact state changes
      contact.onRefresh(function(contact) {
        var newState = contact.getStatus().type;
        console.log('AWS CONNECT: onRefresh - state: ' + newState);
        window.currentContact = contact;
        // If state changed to incoming, notify
        if (newState === 'incoming') {
          notifyIncomingCall(contact);
        }
      });

      // Call connected
      contact.onConnected(function(contact) {
        console.log('AWS CONNECT: CALL CONNECTED!');
        var conn = contact.getInitialConnection();
        var phoneNumber = 'Unknown';
        try {
          if (conn && conn.getEndpoint()) phoneNumber = conn.getEndpoint().phoneNumber || 'Unknown';
        } catch(e) {}
        var data = JSON.stringify({contactId: contactId, phoneNumber: phoneNumber, queue: queueName, type: 'connected'});
        window.cefQuery({request: 'CALL_CONNECTED:' + data});
      });

      // Call accepted (agent answered)
      contact.onAccepted(function(contact) {
        console.log('AWS CONNECT: CALL ACCEPTED!');
        var data = JSON.stringify({contactId: contactId, type: 'accepted'});
        window.cefQuery({request: 'CALL_ACCEPTED:' + data});
      });

      // Call ended
      contact.onEnded(function(contact) {
        console.log('AWS CONNECT: CALL ENDED!');
        window.currentContact = null;
        var data = JSON.stringify({contactId: contactId, type: 'ended'});
        window.cefQuery({request: 'CALL_ENDED:' + data});
      });

      // Missed call
      contact.onMissed(function(contact) {
        console.log('AWS CONNECT: CALL MISSED!');
        window.currentContact = null;
        var data = JSON.stringify({contactId: contactId, type: 'missed'});
        window.cefQuery({request: 'CALL_MISSED:' + data});
      });
    });

    // Agent state events
    connect.agent(function(agent) {
      console.log('AWS CONNECT: Agent connected');
      window.currentAgent = agent;

      agent.onStateChange(function(agentStateChange) {
        var newState = agentStateChange.newState;
        var oldState = agentStateChange.oldState;
        console.log('AWS CONNECT: Agent state changed from ' + oldState + ' to ' + newState);
        window.cefQuery({request: 'AGENT_STATE:' + newState});
      });

      // Initial state
      var currentState = agent.getState().name;
      console.log('AWS CONNECT: Initial agent state: ' + currentState);
      window.cefQuery({request: 'AGENT_STATE:' + currentState});
    });

    console.log('AWS CONNECT: Event listeners setup complete!');

    // Auto-click numpad button if not already on numpad screen
    setTimeout(function() {
      function clickNumpadButton() {
        var buttons = document.querySelectorAll('button');
        for (var i = 0; i < buttons.length; i++) {
          var btn = buttons[i];
          var text = btn.innerText.toLowerCase();
          var ariaLabel = (btn.getAttribute('aria-label') || '').toLowerCase();
          if (text.indexOf('number') >= 0 || text.indexOf('numpad') >= 0 || text.indexOf('dialpad') >= 0 || text.indexOf('dial pad') >= 0 ||
              ariaLabel.indexOf('number') >= 0 || ariaLabel.indexOf('numpad') >= 0 || ariaLabel.indexOf('dialpad') >= 0) {
            console.log('AWS CONNECT: Clicking numpad button: ' + btn.innerText);
            btn.click();
            return true;
          }
        }
        // Also try to find by icon or class
        var dialpadBtn = document.querySelector('[class*="dialpad"], [class*="numpad"], [class*="numberpad"]');
        if (dialpadBtn) {
          console.log('AWS CONNECT: Clicking numpad by class');
          dialpadBtn.click();
          return true;
        }
        return false;
      }

      // Check if numpad is already visible (look for digit buttons)
      var hasNumpad = false;
      var allBtns = document.querySelectorAll('button');
      for (var j = 0; j < allBtns.length; j++) {
        if (allBtns[j].innerText.trim() === '1' || allBtns[j].innerText.indexOf('1') === 0) {
          hasNumpad = true;
          break;
        }
      }

      if (!hasNumpad) {
        console.log('AWS CONNECT: Numpad not visible, trying to open it...');
        clickNumpadButton();
        // Wait for numpad to appear then notify Java
        setTimeout(function checkNumpad() {
          var btns = document.querySelectorAll('button');
          for (var k = 0; k < btns.length; k++) {
            if (btns[k].innerText.trim() === '1' || btns[k].innerText.indexOf('1') === 0) {
              console.log('AWS CONNECT: Numpad is now ready!');
              window.cefQuery({request: 'NUMPAD_READY'});
              return;
            }
          }
          console.log('AWS CONNECT: Waiting for numpad...');
          setTimeout(checkNumpad, 500);
        }, 1000);
      } else {
        console.log('AWS CONNECT: Numpad already visible');
        window.cefQuery({request: 'NUMPAD_READY'});
      }
    }, 2000);

  } else {
    console.log('AWS CONNECT: connect object not found, retrying in 2 seconds...');
    setTimeout(arguments.callee, 2000);
  }
}, 3000);
