(function() {
  var phoneNumber = '{{PHONE_NUMBER}}';
  console.log('OUTBOUND CALL: Dialing ' + phoneNumber);

  if (typeof connect === 'undefined') {
    console.log('OUTBOUND CALL: connect object not found');
    return;
  }

  // First, fill the phone number in CCP input field
  var inputs = document.querySelectorAll('input[type="tel"], input[type="text"]');
  for (var i = 0; i < inputs.length; i++) {
    var input = inputs[i];
    var placeholder = (input.placeholder || '').toLowerCase();
    var ariaLabel = (input.getAttribute('aria-label') || '').toLowerCase();
    if (placeholder.indexOf('number') >= 0 || placeholder.indexOf('phone') >= 0 ||
        ariaLabel.indexOf('number') >= 0 || ariaLabel.indexOf('phone') >= 0) {
      input.value = phoneNumber;
      input.dispatchEvent(new Event('input', {bubbles: true}));
      input.dispatchEvent(new Event('change', {bubbles: true}));
      console.log('OUTBOUND CALL: Phone number filled in CCP input');
      break;
    }
  }

  // Then click the call/dial button
  setTimeout(function() {
    var buttons = document.querySelectorAll('button');
    for (var j = 0; j < buttons.length; j++) {
      var btn = buttons[j];
      var text = (btn.innerText || '').toLowerCase();
      var ariaLabel = (btn.getAttribute('aria-label') || '').toLowerCase();
      if (text.indexOf('call') >= 0 || text.indexOf('dial') >= 0 ||
          ariaLabel.indexOf('call') >= 0 || ariaLabel.indexOf('dial') >= 0) {
        console.log('OUTBOUND CALL: Clicking dial button');
        btn.click();
        return;
      }
    }
    console.log('OUTBOUND CALL: Dial button not found');
  }, 500);
})();
