if (typeof connect !== 'undefined') {
  connect.agent(function(agent) {
    var states = agent.getAgentStates();
    var offlineState = states.find(function(s) { return s.name === 'Offline'; });
    if (offlineState) {
      agent.setState(offlineState, {
        success: function() { console.log('Agent set to Offline'); },
        failure: function() { console.log('Failed to set Offline'); }
      });
    }
  });
}
