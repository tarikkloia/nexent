(function() {
  var digit = '{{DIGIT}}';
  var allButtons = document.querySelectorAll('button');
  for (var i = 0; i < allButtons.length; i++) {
    var btn = allButtons[i];
    var text = btn.innerText.trim();
    if (text === digit || text.indexOf(digit) === 0) {
      btn.click();
      console.log('DTMF: ' + digit);
      return;
    }
  }
})();
