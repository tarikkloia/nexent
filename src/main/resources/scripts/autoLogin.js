setTimeout(function() {
  console.log('AUTO LOGIN: Starting...');

  // AWS Identity Center specific fields
  var userField = document.getElementById('wdc_username');
  var passField = document.getElementById('wdc_password');

  console.log('AUTO LOGIN: userField=' + (userField ? 'found' : 'null') + ', passField=' + (passField ? 'found' : 'null'));

  if (userField && passField) {
    userField.focus();
    userField.value = '{{USERNAME}}';
    userField.dispatchEvent(new Event('input', {bubbles: true}));
    userField.dispatchEvent(new Event('change', {bubbles: true}));
    console.log('AUTO LOGIN: Username filled: ' + userField.value);

    passField.focus();
    passField.value = '{{PASSWORD}}';
    passField.dispatchEvent(new Event('input', {bubbles: true}));
    passField.dispatchEvent(new Event('change', {bubbles: true}));
    console.log('AUTO LOGIN: Password filled');

    setTimeout(function() {
      var btns = document.querySelectorAll('button');
      console.log('AUTO LOGIN: Found ' + btns.length + ' buttons');
      for (var j = 0; j < btns.length; j++) {
        console.log('AUTO LOGIN: Button ' + j + ' text=' + btns[j].innerText);
        var txt = btns[j].innerText.toLowerCase();
        if (txt.indexOf('sign in') >= 0 || txt.indexOf('login') >= 0 || txt.indexOf('submit') >= 0) {
          console.log('AUTO LOGIN: Clicking button: ' + btns[j].innerText);
          btns[j].click();
          break;
        }
      }
    }, 500);
  } else {
    console.log('AUTO LOGIN: Fields not found, retrying in 2 seconds...');
    setTimeout(arguments.callee, 2000);
  }
}, 3000);
