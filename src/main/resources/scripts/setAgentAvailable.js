if (typeof connect !== 'undefined') {
  connect.agent(function(agent) {
    var states = agent.getAgentStates();
    var availableState = states.find(function(s) { return s.name === 'Available'; });
    if (availableState) {
      agent.setState(availableState, {
        success: function() { console.log('Agent set to Available'); },
        failure: function() { console.log('Failed to set Available'); }
      });
    }
  });
}
