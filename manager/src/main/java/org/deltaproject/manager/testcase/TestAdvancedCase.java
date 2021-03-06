package org.deltaproject.manager.testcase;

import org.deltaproject.manager.analysis.ResultAnalyzer;
import org.deltaproject.manager.analysis.ResultInfo;
import org.deltaproject.manager.core.*;
import org.deltaproject.manager.utils.AgentLogger;
import org.deltaproject.webui.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;

import static org.deltaproject.webui.TestCase.TestResult.FAIL;
import static org.deltaproject.webui.TestCase.TestResult.PASS;

public class TestAdvancedCase {
    private static final Logger log = LoggerFactory.getLogger(TestAdvancedCase.class.getName());

    private Configuration cfg = Configuration.getInstance();

    private AppAgentManager appm;
    private HostAgentManager hostm;
    private ChannelAgentManager channelm;
    private ControllerManager controllerm;
    private String targetIP;

    private ResultAnalyzer analyzer;

    public TestAdvancedCase(AppAgentManager am, HostAgentManager hm, ChannelAgentManager cm, ControllerManager ctm) {
        this.appm = am;
        this.hostm = hm;
        this.channelm = cm;
        this.controllerm = ctm;
        this.analyzer = new ResultAnalyzer(controllerm, appm);
        this.targetIP = cfg.getCONTROLLER_IP();
    }

    public void runRemoteAgents(boolean channel, boolean host) {
        log.info("Run controller/channel/host agents..");

        appm.setTargetController(controllerm.getType());

        if (channel) {
            channelm.runAgent();
        }

        if (host) {
            hostm.runAgent("test-advanced-topo.py");
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        initController(true);
    }

//    // for new cases (3.1.210, 3.1.220, 3.1.230, 3.1.240)
//    public void newRunRemoteAgents(boolean channel, boolean host) {
//        log.info("Run controller/channel/host agents..");
//
//        appm.setTargetController(controllerm.getType());
//
//        if (channel) {
//            channelm.runAgent();
//        }
//
//        if (host) {
//            hostm.runAgent("test-advanced-topo.py");
//        }
//
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        initController(true);
//    }

    public void stopRemoteAgents() {
        log.info("Stop controller/channel/host agents..");
        controllerm.killController();
        hostm.stopAgent();
        channelm.stopAgent();

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void replayKnownAttack(TestCase test) {
        switch (test.getcasenum()) {
            case "3.1.001":
            case "3.1.002":
            case "3.1.003":
            case "3.1.004":
            case "3.1.005":
                runRemoteAgents(false, true);
                testInconsistency(test);
                break;
            case "3.1.010":
                runRemoteAgents(true, true);
                testPacketInFlooding(test);
                break;
            case "3.1.020":
                runRemoteAgents(false, true);
                testControlMessageDrop(test);
                break;
            case "3.1.030":
                runRemoteAgents(false, true);
                testInfiniteLoop(test);
                break;
            case "3.1.040":
                runRemoteAgents(false, true);
                testInternalStorageAbuse(test);
                break;
            case "3.1.050":
//                runRemoteAgents(true, true);
                testSwitchTableFlooding(test);
                return;
            case "3.1.060":
                runRemoteAgents(true, true);
                testSwitchIdentificationSpoofing(test);
                break;
            case "-------":         // testSwitchOFCase
                testMalformedControlMessage(test);
                break;
            case "3.1.070":
                runRemoteAgents(false, true);
                testFlowRuleModification(test);
                break;
            case "3.1.080":
                runRemoteAgents(false, true);
                testFlowTableClearance(test);
                break;
            case "3.1.090":
                runRemoteAgents(false, true);
                testEventUnsubscription(test);
                break;
            case "3.1.100":
                if (controllerm.getType().equals("Floodlight")) {
                    log.info("\nIt is not possible to replay this attack in Floodlight [" + test.getcasenum() + "] ");
                    return;
                }
                runRemoteAgents(false, true);
                testApplicationEviction(test);
                break;
            case "3.1.110":
                runRemoteAgents(false, true);
                testMemoryExhaustion(test);
                break;
            case "3.1.120":
                runRemoteAgents(false, true);
                testCPUExhaustion(test);
                break;
            case "3.1.130":
                runRemoteAgents(false, true);
                testSystemVariableManipulation(test);
                break;
            case "3.1.140":
                runRemoteAgents(false, true);
                testSystemCommandExecution(test);
                break;
            case "3.1.160":
                runRemoteAgents(true, true);
                testLinkFabrication(test);
                break;
            case "3.1.170":
                runRemoteAgents(true, true);
                testEvaseDrop(test);
                break;
            case "3.1.180":
                runRemoteAgents(true, true);
                testManInTheMiddle(test);
                break;
            case "3.1.190":
                runRemoteAgents(false, true);
                testFlowRuleFlooding(test);
                break;
            case "3.1.200":
                runRemoteAgents(false, true);
                testSwitchFirmwareMisuse(test);
                break;
            case "3.1.210": //TODO check needed
                runRemoteAgents(false, true);
                testPacketInDataForge(test);
                break;
            case "3.1.220": //TODO check needed
                runRemoteAgents(true, true);
                testMalformedFlowRuleGeneration(test);
                break;
            case "3.1.230": //TODO check needed
                changeFloodlightProperties("1");
                runRemoteAgents(false, true);
                testFlowRuleIDSpoofing(test);
                changeFloodlightProperties("2");
                break;
            case "3.1.240": //TODO check needed
                runRemoteAgents(false, true);
                testInfiniteFlowRuleSynchronization(test);
                break;
            case "------ ":          // testControllerOFCase
                testControlMessageManipulation(test);
                break;
        }

        stopRemoteAgents();
    }

    private void changeFloodlightProperties(String num) {
        Process proc = null;
        String[] cmdArray = null;
        try {
            cmdArray = new String[]{System.getenv("DELTA_ROOT") +
                "/tools/dev/app-agent-setup/floodlight/floodlight-1.2-case3-scp", num};

            ProcessBuilder pb = new ProcessBuilder(cmdArray);
            pb.redirectErrorStream(true);
            proc = pb.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initController(boolean switchWait) {
        if (!controllerm.isRunning()) {
            log.info("Run target controller: " + controllerm.getType() + " " + controllerm.getVersion());

            if (!controllerm.createController()) {
                log.info("Target controller setup is failed");
                return;
            }

			/* waiting for switches */
            log.info("Listen to switches..");
            controllerm.isConnectedSwitch(switchWait);
            log.info(cfg.getSwitchList().size() + " switch(es) connected");

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public String generateFlow(String proto) {
        hostm.write(proto);
        String result = hostm.read();

        return result;
    }

    /*
     * 3.1.001-005 - Inconsistency check
     */
    public boolean testInconsistency(TestCase test) {
        appm.write(test.getcasenum());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        String result = appm.read();
        if (result.equals("success"))
            test.setResult(PASS);
        else if (result.equals("fail"))
            test.setResult(FAIL);
        else System.out.println(result);
        return true;
    }


    /*
 * 3.1.010 - Packet-In Flooding
 */
    public boolean testPacketInFlooding(TestCase test) {
//        //log.info(test.getcasenum() + " - Packet-In Flooding - Test for controller protection against Packet-In Flooding");

        String before = generateFlow("ping");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* clear flow rules */
        appm.write("3.1.080|false");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* step 2: run cbench */
        log.info("Cbench starts");
        channelm.write(test.getcasenum());

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String after = generateFlow("ping");
        log.info("Agent-Manager retrieves result from Host-Agent");

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON).addType(ResultInfo.LATENCY_TIME);
        result.setLatency(before, after);

		/* step 4: decide if the attack is feasible */
        analyzer.checkResult(test, result);

        return true;
    }

    /*
     * 3.1.020 - Control Message Drop
     */
    public boolean testControlMessageDrop(TestCase test) {
//        //log.info(test.getcasenum() + " - Control Message Drop - Test for controller protection against application dropping control messages");

		/* step 2: conduct the attack */
        appm.write(test.getcasenum());
//        log.info("App-Agent set Packet-In msg drop [" + appm.read() + "]");
        log.info("App-Agent set Packet-In msg drop");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String flowResult = generateFlow("ping");

        log.info("Agent-Manager retrieves result from App Agent");

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);

        appm.write("3.1.020|getmsg");
        String appresult = appm.read();
        log.info("Dropped Packet-In: " + appresult);

        result.setLatency(null, flowResult);

        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.030 - Infinite Loop
     */
    public boolean testInfiniteLoop(TestCase test) {
        //log.info(test.getcasenum() + " - Infinite Loop - Test for controller protection against application creating infinite loop");

		/* step 2: conduct the attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String flowResult = generateFlow("ping");

        log.info("Agent-Manager retrieves result from Host-Agent");

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, flowResult);

        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.040 - Internal Storage Abuse
     */
    public boolean testInternalStorageAbuse(TestCase test) {
        //log.info(test.getcasenum() + " - Internal Storage Abuse - Test for controller protection against application manipulating network information base");

		/* step 2: try communication */
        log.info("HostAgent starts communication");
        String flowResult = generateFlow("ping");
        log.info("Gathering result from HostAgent");

		/* step 3: conduct the attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        log.info("Agent-Manager retrieves result from App-Agent");
        String removedItem = appm.read();
        log.info("Removed Item: ");

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.APPAGENT_REPLY);
        result.setResult(removedItem);

        analyzer.checkResult(test, result);

        //appm.closeSocket();

        return true;
    }

    /*
     * 3.1.050 - Switch Table Flooding
     */
    public boolean testSwitchTableFlooding(TestCase test) {
        log.info(test.getcasenum() + " - Device Inventory Table Flooding - Test for controller protection against device inventory table flooding");

		/* step 2: conduct the attack */
        log.info("Channel-Agent starts");
        channelm.write(test.getcasenum());
        String resultChannel = channelm.read();
        log.info("Agent-Manager retrieves result from Channel-Agent");

		/* step 4: decide if the attack is feasible */
        // analyzer.checkSwirchState(code);

        channelm.write("exit");
        controllerm.flushARPcache();
        //appm.closeSocket();

        return true;
    }

    /*
     * 3.1.060 - Switch Identification Spoofing
     */
    public boolean testSwitchIdentificationSpoofing(TestCase test) {
        //log.info(test.getcasenum() + " - Switch Identification Spoofing - Test for switch protection against ID spoofing");

		/* step 2: conduct the attack */
        log.info("Channel-Agent starts");
        channelm.write(test.getcasenum());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String flowResult = generateFlow("ping");

        log.info("Agent-Manager retrieves result from Host-Agent");

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, flowResult);

        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * ---- Malformed Control Message
     */
    public boolean testMalformedControlMessage(TestCase test) {
        //log.info(test.getcasenum() + " - Malformed Control Message");

		/* step 2: conduct the attack */
        log.info("ChannelAgent starts");
        channelm.write(test.getcasenum());

        log.info("Gathering result from channel agent");
        String resultChannel = channelm.read();

		/* step 3: try communication */
        log.info("HostAgent generates flow");
        this.generateFlow("ping");

        log.info("HostAgent starts communication with another host");
        String resultFlow = generateFlow("ping");

		/* step 4: decide if the attack is feasible */
        log.info("Check switch state");

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.SWITCH_STATE);

        analyzer.checkResult(test, result);

        channelm.write("exit");
        controllerm.flushARPcache();
        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.070 - Flow Rule Modification
     */
    public boolean testFlowRuleModification(TestCase test) {
        //log.info(test.getcasenum() + " - Flow Rule Modification - Test for switch protection against application modifying flow rule");

        String before = generateFlow("ping");
        log.info("Host-Agent sends packets to others (before)");

		/* step 2 : replay attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        String modified = "";

        log.info("Agent-Manager retrieves result from App-Agent");
        modified = appm.read();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String after = generateFlow("ping");

        ResultInfo result = new ResultInfo();

        log.info("Agent-Manager retrieves result from App-Agent and Host-Agent");
        /* step 4: decide if the attack is feasible */
        result.addType(ResultInfo.APPAGENT_REPLY);
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, after);
        result.setResult(modified);

        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.080 - Flow Table Clearance
     */
    public boolean testFlowTableClearance(TestCase test) {
        //log.info(test.getcasenum() + " - Flow Table Clearance - Test for controller protection against flow table flushing");

        log.info("Host-Agent sends packets to others (before)");

        /* step 2 : generate before flow (ping) */
        String before = generateFlow("compare");

		/* step 3 : replay attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        log.info("Host-Agent sends packets to others (after)");
        String after = generateFlow("compare");

        ResultInfo result = new ResultInfo();

		/* step 4: decide if the attack is feasible */
        result.addType(ResultInfo.LATENCY_TIME);
        result.setLatency(before, after);

        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.090 - Event Unsubscription
     */
    public boolean testEventUnsubscription(TestCase test) {
        if (controllerm.getType().equals("ONOS")) {
            log.info("ONOS is impossible to replay [" + test.getcasenum() + "] ");
            return false;
        }

        //log.info(test.getcasenum() + " - Event Unsubscription - Test for controller protection against application unsubscribing neighbour application from events");

		/* step 2: conduct the attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        String remove = "";

        if (controllerm.getType().equals("OpenDaylight"))
            remove = appm.read2();
        else
            remove = appm.read();

        log.info("Removed Item: " + remove);

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String resultFlow = generateFlow("ping");
        log.info("Agent-Manager retrieves result from Host-Agent");

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, resultFlow);
        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.100 - Application Eviction
     */
    public boolean testApplicationEviction(TestCase test) {

        //log.info(test.getcasenum() + " - Application Eviction - Test for controller protection against one application uninstalling another application");

		/* step 2: conduct the attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        String remove = "";

        if (controllerm.getType().equals("OpenDaylight"))
            remove = appm.read2();
        else
            remove = appm.read();

        log.info("Removed Item: " + remove);

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String resultFlow = generateFlow("ping");
        log.info("Agent-Manager retrieves result from Host-Agent");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, resultFlow);

        analyzer.checkResult(test, result);

        if (controllerm.getType().equals("OpenDaylight") && controllerm.getVersion().equals("carbon")) {
            appm.write("restore");
            log.info("Restart evicted applications.. " + appm.read2());
        }

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.110 - Memory Exhaustion
     */
    public boolean testMemoryExhaustion(TestCase test) {
        //log.info(test.getcasenum() + " - Memory Exhaustion - Test for controller protection against an application exhausting controller memory");

		/* step 2: conduct the attack */
        log.info("Host-Agent sends packets to others (before)");
        /* step 2 : generate before flow (ping) */
        String before = generateFlow("compare");

		/* remove flow rules */
        appm.write("3.1.80|false");

		/* step 3 : replay attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("Host-Agent sends packets to others (after)");
        String after = generateFlow("compare");

        ResultInfo result = new ResultInfo();

		/* step 4: decide if the attack is feasible */
        result.addType(ResultInfo.COMMUNICATON);
        result.addType(ResultInfo.LATENCY_TIME);
        result.setLatency(before, after);

        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.120 - CPU Exhaustion
     */
    public boolean testCPUExhaustion(TestCase test) {
        //log.info(test.getcasenum() + " - CPU Exhaustion - Test for controller protection against an application exhausting controller CPU");

		/* step 2: conduct the attack */
        log.info("Host-Agent sends packets to others (before)");

        /* step 2 : generate before flow (ping) */
        String before = generateFlow("compare");

		/* remove flow rules one time */
        appm.write("3.1.80|false");

		/* step 3 : replay attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("Host-Agent sends packets to others (after)");
        String after = generateFlow("compare");

        ResultInfo result = new ResultInfo();

		/* step 4: decide if the attack is feasible */
        result.addType(ResultInfo.COMMUNICATON);
        result.addType(ResultInfo.LATENCY_TIME);
        result.setLatency(before, after);

        analyzer.checkResult(test, result);

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.130 - System Variable Manipulation
     */
    public boolean testSystemVariableManipulation(TestCase test) {
        //log.info(test.getcasenum() + " - System Variable Manipulation - Test for controller protection against an application manipulating a system variable");

        long start = System.currentTimeMillis();

		/* step 2: conduct the attack */
        log.info("App-Agent starts");

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        this.generateFlow("ping");

        appm.write(test.getcasenum());

		/* step 4: decide if the attack is feasible */
        log.info("Agent-Manager checks the status of switches");

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.SWITCH_STATE);
        analyzer.checkResult(test, result);

        long end = System.currentTimeMillis();
        log.info("Running Time: " + (end - start));

        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.140 - System Command Execution
     */
    public boolean testSystemCommandExecution(TestCase test) {
        //log.info(test.getcasenum() + " - System Command Execution - Test for controller protection against an application accessing a system command");

		/* step 2: conduct the attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("Agent-Manager checks the status of handler controller");

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.CONTROLLER_STATE);
        if (analyzer.checkResult(test, result)) {
            //appm.closeSocket();
        }
        return true;
    }

	/*
     * 3.1.150 - Host Location Hijacking : Not implemented yet
	 */


    /*
     * 3.1.160 - Link Fabrication ; incomplete
     */
    public boolean testLinkFabrication(TestCase test) {
        //log.info(test.getcasenum() + " - Link Fabrication - Test for controller protection against application creating fictitious link");

		/* step 1: conduct the attack */
        log.info("Channel-Agent starts");
        channelm.write(test.getcasenum());
        channelm.read();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        /* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String resultFlow = generateFlow("ping");
        log.info("Agent-Manager retrieves result from Host-Agent");

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, resultFlow);
        analyzer.checkResult(test, result);

		/* step 4: decide if the attack is feasible */
        // analyzer.checkSwirchState(code);

        channelm.write("exit");
        controllerm.flushARPcache();
        //appm.closeSocket();

        return true;
    }

    /*
     * 3.1.170 - Eavesdrop
     */
    public boolean testEvaseDrop(TestCase test) {
        controllerm.flushARPcache();
        //log.info(test.getcasenum() + " - Eavesdrop - Test for control channel protection against malicious host sniffing the control channel");
        String resultChannel;

        try {
            Thread.sleep(10000);    // 30 seconds
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        /* step 2: conduct the attack */
        log.info("Channel-Agent starts");
        channelm.write(test.getcasenum());

        try {
            Thread.sleep(10000);    // 30 seconds
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");

        generateFlow("ping");

        try {
            Thread.sleep(10000);    // 30 seconds
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("Agent-Manager retrieves the result from Channel-Agent");
        channelm.write(test.getcasenum() + "-2");
        resultChannel = channelm.read();

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.CHANNELAGENT_REPLY);
        result.setResult(resultChannel);
        analyzer.checkResult(test, result);

        channelm.write("exit");
        controllerm.flushARPcache();
        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.180 - MITM
     */
    public boolean testManInTheMiddle(TestCase test) {
        controllerm.flushARPcache();
        //log.info(test.getcasenum() + " - Man-In-The-Middle attack - Test for control channel protection against MITM attack");

		/* step 2: conduct the attack */
        log.info("Channel-Agent starts");
        channelm.write(test.getcasenum());
        String resultChannel = channelm.read();

        try {
            Thread.sleep(30000);    // 30 seconds
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others");
        String resultFlow = generateFlow("ping");
        log.info("Agent-Manager retrieves the result from Host-Agent");

		/* step 4: decide if the attack is feasible */
        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, resultFlow);
        analyzer.checkResult(test, result);

        channelm.write("exit");
        controllerm.flushARPcache();
        // //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.190 - Flow Rule Flooding
     */
    public boolean testFlowRuleFlooding(TestCase test) {
        //log.info(test.getcasenum() + " - Flow Rule Flooding - Test for switch protection against flow rule flooding");

        log.info("Host-Agent sends packets to others (before)");
        String before = generateFlow("compare");

		/* remove flow rules */
        appm.write("3.1.80|false");

		/* step 2: conduct the attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others (after)");
        String after = generateFlow("compare");

		/* step 4: decide if the attack is feasible */
        log.info("Agent-Manager retrieves the result from Host-Agent");

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON).addType(ResultInfo.LATENCY_TIME);
        result.setLatency(before, after);

        analyzer.checkResult(test, result);

        //appm.closeSocket();

        return true;
    }

    /*
     * 3.1.200 - Switch Firmware Misuse
     */
    public boolean testSwitchFirmwareMisuse(TestCase test) {
        //log.info(test.getcasenum() + " - Switch Firmware Misuse - Test for switch protection against application installing degraded flow rules");

        log.info("Host-Agent sends packets to others (before)");
        String before = generateFlow("compare");

		/* step 2: conduct the attack */
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("Agent-Manager retrieves the result from App-Agent");
        String modified = appm.read();
        log.info("Modified Item: " + modified);

		/* step 3: try communication */
        log.info("Host-Agent sends packets to others (after)");
        String after = generateFlow("compare");

		/* step 4: decide if the attack is feasible */
        log.info("Agent-Manager retrieves the result from Host-Agent");

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.LATENCY_TIME);
        result.setLatency(before, after);
        analyzer.checkResult(test, result);

        //appm.closeSocket();
        controllerm.killController();

        return true;
    }

    /*
     * 3.1.210 - Packet-In Data Forge
     */
    public boolean testPacketInDataForge(TestCase test) {
		log.info("* Test | The AM instructs the app agent to randomize the sequence of the packet-In subscription list");
        if (!controllerm.getType().equals("Floodlight")) {
            log.info("* Test | Floodlight is only possible to replay [" + test.getcasenum() + "] ");
            return false;
        }

/*        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
*/
        log.info("App-Agent starts");
        appm.write(test.getcasenum());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("* Test | Host-Agent sends packets to others");
        String flowResult = generateFlow("ping");

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, flowResult);
        analyzer.checkResult(test, result);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        //appm.closeSocket();
        return true;
    }

    /*
     * 3.1.220 - Malformed Flow Rule Generation (case 2)
     */
    public boolean testMalformedFlowRuleGeneration(TestCase test) {
        try {

            if (!controllerm.getType().equals("OpenDaylight")) {
                log.info("* Test | OpenDaylight is only possible to replay [" + test.getcasenum() + "] ");
                return false;
            }

            log.info("App-Agent starts");
            Thread.sleep(10000);

            log.info("[Attack] Install malformed rules in configurational data store from OpenDaylight");
            appm.write(test.getcasenum());
            String appResult = appm.read();
//            log.info(result);

            Thread.sleep(10000);

            log.info("Instruct Channel Agent to interrupt control channel temporarily");
            channelm.write(test.getcasenum());

            Thread.sleep(20000);

            log.info("Host-Agent sends packets to others");
            String flowResult = generateFlow("ping");

            ResultInfo result = new ResultInfo();
            result.addType(ResultInfo.COMMUNICATON);
            result.setLatency(null, flowResult);

            analyzer.checkResult(test, result);

//            log.info("[Restore] Remove the malformed rules in configurational data store from OpenDaylight");
//            appm.write(test.getcasenum() + "|remove");
//            appResult = appm.read();
//            log.info(appResult);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return true;
    }


    /*
     * 3.1.230 - Flow Rule ID Spoofing (case 3)
     */
    public boolean testFlowRuleIDSpoofing(TestCase test) {
        if (!controllerm.getType().equals("Floodlight")) {
            log.info("* Test | Floodlight is only possible to replay [" + test.getcasenum() + "] ");
            return false;
        }
        log.info("* Test | The AM instructs the app agent to install default flow rules");
		log.info("* SendPKT | CMD : Manager --> App agent = Install default flow rules");

        String[] setupCmd = new String[3];
        setupCmd[0] = "sh";
        setupCmd[1] = System.getenv("DELTA_ROOT") + "/tools/util/blackhat/case3/setup.sh";
        setupCmd[2] = targetIP;
        try {
            Process p = Runtime.getRuntime().exec(setupCmd);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("* Test | The AM instructs the app agent to install a flow rule with spoofed flow ID");
		log.info("* SendPKT | CMD : Manager --> App agent = Install spoofed flow ID");

        String[] attackCmd = new String[3];
        attackCmd[0] = "sh";
        attackCmd[1] = System.getenv("DELTA_ROOT") + "/tools/util/blackhat/case3/attack.sh";
        attackCmd[2] = targetIP;

        try {
            Process p = Runtime.getRuntime().exec(attackCmd);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("Check inconsistency between the controller and switches");

        appm.write(test.getcasenum());

        String resultStr = appm.read();
        if (!resultStr.equals("nothing")) {
            log.info("Inconsistency Flow Rule: " + resultStr);
        }

        log.info("Host-Agent sends packets to others");
        String flowResult = generateFlow("ping");

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.COMMUNICATON);
        result.setLatency(null, flowResult);
        analyzer.checkResult(test, result);
        //appm.closeSocket();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return true;
    }

    /*
     * 3.1.240 - Infinite Flow Rule Synchronization (case 4)
     */
    public boolean testInfiniteFlowRuleSynchronization(TestCase test) {
        if (!controllerm.getType().equals("ONOS")) {
            log.info("* Test | ONOS is only possible to replay [" + test.getcasenum() + "] ");
            return false;
        }

        log.info("* Test | Setup test environment");
        log.info("* SendPKT | CMD : Manager --> App agent = Setup test environment");

        String[] setupCmd = new String[3];
        setupCmd[0] = "sh";
        setupCmd[1] = System.getenv("DELTA_ROOT") + "/tools/util/blackhat/case4/setup.sh";
        setupCmd[2] = targetIP;
        try {
            Process p = Runtime.getRuntime().exec(setupCmd);
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Setup complete");

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log.info("* Test | Infinite Flow Rule Synchronization Attack");
        log.info("* SendPKT | CMD : Manager --> App agent = instructions:[port=999999]");

        String[] attackCmd = new String[3];
        attackCmd[0] = "sh";
        attackCmd[1] = System.getenv("DELTA_ROOT") + "/tools/util/blackhat/case4/attack.sh";
        attackCmd[2] = targetIP;
        try {
            Process p = Runtime.getRuntime().exec(attackCmd);
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Attack complete");

        try {
            Thread.sleep(40000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        appm.write(test.getcasenum());

        String result = appm.read();

        if (result.equals("nothing")) {
            test.setResult(TestCase.TestResult.PASS);
            log.info("* Test | 3.1.240, PASS");
        } else {
            log.info("Inconsistency Flow Rule: " + result);
            test.setResult(TestCase.TestResult.FAIL);
            log.info("* Test | 3.1.240, FAIL");
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        //appm.closeSocket();
        return true;
    }

    /*
     * ---- Control Message Manipulation
     */
    public boolean testControlMessageManipulation(TestCase test) {
        //log.info(test.getcasenum() + " - Control Message Manipulation");

		/* step 2: conduct the attack */
        log.info("ChannelAgent starts");
        channelm.write(test.getcasenum());
        String resultChannel = channelm.read();

		/* step 3: try communication */
        log.info("HostAgent generates flow");
        generateFlow("ping");

		/* step 4: decide if the attack is feasible */
        log.info("Check switch state");

        ResultInfo result = new ResultInfo();
        result.addType(ResultInfo.SWITCH_STATE);
        analyzer.checkResult(test, result);

        channelm.write("exit");
        controllerm.flushARPcache();
        //appm.closeSocket();
        return true;
    }
}
