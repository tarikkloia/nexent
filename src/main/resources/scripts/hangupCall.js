if (window.currentContact) {
  var conn = window.currentContact.getAgentConnection();
  if (conn) conn.destroy();
  console.log('Call ended via Java button');
}
