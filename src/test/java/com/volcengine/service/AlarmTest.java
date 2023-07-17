package com.volcengine.service;

import com.volcengine.model.tls.Const;
import com.volcengine.model.tls.QueryRequest;
import com.volcengine.model.tls.Receiver;
import com.volcengine.model.tls.RequestCycle;
import com.volcengine.model.tls.exception.LogException;
import com.volcengine.model.tls.request.*;
import com.volcengine.model.tls.response.*;
import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.*;

public class AlarmTest extends BaseTest {
    @Test
    public void testAlarm() {
        SimpleDateFormat sdf = new SimpleDateFormat(Const.DATE_FORMAT);
        String prefix = "test";
        String separator = "-";
        String testReceiver =  System.getenv("receiver");;
        String date = sdf.format(new Date());
        try {
            // create alarm notify group
            CreateAlarmNotifyGroupRequest createAlarmNotifyGroupRequest = new CreateAlarmNotifyGroupRequest();
            final String alarmNotifyGroupName = prefix + separator + System.currentTimeMillis();
            createAlarmNotifyGroupRequest.setAlarmNotifyGroupName(alarmNotifyGroupName);
            createAlarmNotifyGroupRequest.setNotifyType(Arrays.asList("Recovery", "Trigger"));
            Receiver receiver = new Receiver();
            receiver.setReceiverType("User");
            receiver.setReceiverNames(Arrays.asList(testReceiver));
            receiver.setReceiverChannels(Arrays.asList("Sms"));
            receiver.setStartTime("00:00:00");
            receiver.setEndTime("23:59:59");
            createAlarmNotifyGroupRequest.setReceivers(Arrays.asList(receiver));
            CreateAlarmNotifyGroupResponse createAlarmNotifyGroupResponse = client.createAlarmNotifyGroup(
                    createAlarmNotifyGroupRequest);
            assertTrue(createAlarmNotifyGroupResponse.getAlarmNotifyGroupId().length() > 0);

            Exception exception = assertThrows(LogException.class, () -> {
                client.createAlarmNotifyGroup(createAlarmNotifyGroupRequest);
            });
            String expectedMessage = "Notify group already exist";
            String actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));
            System.out.println("create alarm notify group success,response:" + createAlarmNotifyGroupResponse);

            exception = assertThrows(LogException.class, () -> {
                createAlarmNotifyGroupRequest.setAlarmNotifyGroupName(alarmNotifyGroupName);
                createAlarmNotifyGroupRequest.setReceivers(null);
                client.createAlarmNotifyGroup(createAlarmNotifyGroupRequest);
            });

            // describe alarm notify group
            final DescribeAlarmNotifyGroupsRequest describeAlarmNotifyGroupsRequest =
                    new DescribeAlarmNotifyGroupsRequest((createAlarmNotifyGroupResponse.getAlarmNotifyGroupId()));
            DescribeAlarmNotifyGroupsResponse describeAlarmNotifyGroupsResponse =
                    client.describeAlarmNotifyGroups(describeAlarmNotifyGroupsRequest);
            Assert.assertEquals(describeAlarmNotifyGroupsResponse.getAlarmNotifyGroups().get(0).
                    getAlarmNotifyGroupName(), createAlarmNotifyGroupRequest.getAlarmNotifyGroupName());

            exception = assertThrows(LogException.class, () -> {
                describeAlarmNotifyGroupsRequest.setAlarmNotifyGroupId(createAlarmNotifyGroupResponse.getAlarmNotifyGroupId());
                describeAlarmNotifyGroupsRequest.setPageSize(0);
                client.describeAlarmNotifyGroups(describeAlarmNotifyGroupsRequest);
            });

            exception = assertThrows(LogException.class, () -> {
                describeAlarmNotifyGroupsRequest.setAlarmNotifyGroupId("zsq_123");
                client.describeAlarmNotifyGroups(describeAlarmNotifyGroupsRequest);
            });
            expectedMessage = "Invalid argument key";
            actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));
            System.out.println("describe alarm notify group success,response:" + describeAlarmNotifyGroupsResponse);

            // modify alarm notify group
            ModifyAlarmNotifyGroupRequest modifyAlarmNotifyGroupRequest = new ModifyAlarmNotifyGroupRequest();
            modifyAlarmNotifyGroupRequest.setAlarmNotifyGroupId(createAlarmNotifyGroupResponse.getAlarmNotifyGroupId());
            modifyAlarmNotifyGroupRequest.setAlarmNotifyGroupName(
                    createAlarmNotifyGroupRequest.getAlarmNotifyGroupName() + separator + System.currentTimeMillis());
            modifyAlarmNotifyGroupRequest.setNotifyType(Arrays.asList("Recovery", "Trigger"));
            receiver = new Receiver();
            receiver.setReceiverType("User");
            receiver.setReceiverNames(Arrays.asList(testReceiver));
            receiver.setReceiverChannels(Arrays.asList("Sms"));
            receiver.setStartTime("01:00:00");
            receiver.setEndTime("22:59:59");
            modifyAlarmNotifyGroupRequest.setReceivers(Arrays.asList(receiver));
            ModifyAlarmNotifyGroupResponse modifyAlarmNotifyGroupResponse =
                    client.modifyAlarmNotifyGroup(modifyAlarmNotifyGroupRequest);

            DescribeAlarmNotifyGroupsRequest describeAlarmNotifyGroupsRequest3 = new DescribeAlarmNotifyGroupsRequest();
            describeAlarmNotifyGroupsRequest3.
                    setAlarmNotifyGroupId(createAlarmNotifyGroupResponse.getAlarmNotifyGroupId());
            describeAlarmNotifyGroupsResponse =
                    client.describeAlarmNotifyGroups(describeAlarmNotifyGroupsRequest3);
            Assert.assertEquals(modifyAlarmNotifyGroupRequest.getAlarmNotifyGroupName(),
                    describeAlarmNotifyGroupsResponse.getAlarmNotifyGroups().get(0).getAlarmNotifyGroupName());
            exception = assertThrows(LogException.class, () -> {
                modifyAlarmNotifyGroupRequest.setAlarmNotifyGroupId("test_1243_456");
                client.modifyAlarmNotifyGroup(modifyAlarmNotifyGroupRequest);
            });
            expectedMessage = "Invalid argument key";
            actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));
            System.out.println("modify alarm notify group success,response:" + modifyAlarmNotifyGroupResponse);

            exception = assertThrows(LogException.class, () -> {
                modifyAlarmNotifyGroupRequest.setAlarmNotifyGroupId(createAlarmNotifyGroupResponse.getAlarmNotifyGroupId());
                modifyAlarmNotifyGroupRequest.setNotifyType(Arrays.asList("invalid"));
                client.modifyAlarmNotifyGroup(modifyAlarmNotifyGroupRequest);
            });

            // create project for alarm
            String projectName = prefix + "project" + separator + date + separator + System.currentTimeMillis();
            String region = clientConfig.getRegion();
            String description = "test project";
            CreateProjectRequest project = new CreateProjectRequest(projectName, region, description);
            CreateProjectResponse createProjectResponse = client.createProject(project);
            System.out.println("create project success,response:" + createProjectResponse);
            String projectId = createProjectResponse.getProjectId();
            //create topic for alarm
            String topicName = prefix + "topic" + separator + date + separator + System.currentTimeMillis();
            CreateTopicRequest createTopicRequest = new CreateTopicRequest();
            createTopicRequest.setTopicName(topicName);
            createTopicRequest.setProjectId(projectId);
            createTopicRequest.setTtl(500);
            CreateTopicResponse createTopicResponse = client.createTopic(createTopicRequest);
            System.out.println("create topic success,response:" + createTopicResponse);
            // create alarm
            CreateAlarmRequest createAlarmRequest = new CreateAlarmRequest();
            createAlarmRequest.setAlarmNotifyGroup(
                    Arrays.asList(createAlarmNotifyGroupResponse.getAlarmNotifyGroupId()));
            createAlarmRequest.setCondition("$1.errNum>0");
            String nameNew = prefix + separator + System.currentTimeMillis();
            createAlarmRequest.setAlarmName(nameNew);
            createAlarmRequest.setAlarmPeriod(60);
            createAlarmRequest.setProjectId(createProjectResponse.getProjectId());
            QueryRequest queryRequest = new QueryRequest();
            queryRequest.setNumber(1);
            queryRequest.setQuery("Failed | select count(*) as errNum");
            queryRequest.setStartTimeOffset(-1);
            queryRequest.setEndTimeOffset(0);
            queryRequest.setTopicId(createTopicResponse.getTopicId());
            createAlarmRequest.setQueryRequest(Arrays.asList(queryRequest));
            RequestCycle period = new RequestCycle();
            period.setType("Period");
            period.setTime(10);
            createAlarmRequest.setRequestCycle(period);
            createAlarmRequest.setTriggerPeriod(1);
            createAlarmRequest.setUserDefineMsg("test-zsq");
            CreateAlarmResponse createAlarmResponse = client.createAlarm(createAlarmRequest);
            assertTrue(createAlarmResponse.getAlarmId().length() > 0);

            exception = assertThrows(LogException.class, () -> {
                client.createTopic(createTopicRequest);
            });
            expectedMessage = "Topic " + createTopicRequest.getTopicName() + " already exist";
            actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));
            System.out.println("create alarm success,response:" + createAlarmResponse);
            // describe alarm
            final DescribeAlarmsRequest describeAlarmsRequest = new DescribeAlarmsRequest();
            describeAlarmsRequest.setProjectId(createProjectResponse.getProjectId());
            describeAlarmsRequest.setPageNumber(1);
            describeAlarmsRequest.setPageSize(10);
            DescribeAlarmsResponse describeAlarmsResponse = client.describeAlarms(describeAlarmsRequest);
            Assert.assertEquals(describeAlarmsResponse.getAlarms().get(0).getAlarmName(),
                    createAlarmRequest.getAlarmName());

            exception = assertThrows(LogException.class, () -> {
                describeAlarmsRequest.setAlarmId("zsq_124_356");
                client.describeAlarms(describeAlarmsRequest);
            });
            expectedMessage = "Invalid argument key";
            actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));
            System.out.println("describe alarm success,response:" + describeAlarmsResponse);

            //modify alarm
            ModifyAlarmRequest modifyAlarmRequest = new ModifyAlarmRequest();
            modifyAlarmRequest.setAlarmId(createAlarmResponse.getAlarmId());
            modifyAlarmRequest.setAlarmName(createAlarmRequest.getAlarmName() + separator + System.currentTimeMillis());
            queryRequest = new QueryRequest();
            queryRequest.setNumber(1);
            queryRequest.setQuery("Failed | select count(1) as errNum");
            queryRequest.setStartTimeOffset(-2);
            queryRequest.setEndTimeOffset(0);
            queryRequest.setTopicId(createTopicResponse.getTopicId());
            modifyAlarmRequest.setQueryRequest(Arrays.asList(queryRequest));
            period = new RequestCycle();
            period.setType("Period");
            period.setTime(10);
            modifyAlarmRequest.setRequestCycle(period);
            modifyAlarmRequest.setTriggerPeriod(1);
            modifyAlarmRequest.setUserDefineMsg("test-zsq-modified");
            ModifyAlarmResponse modifyAlarmResponse = client.modifyAlarm(modifyAlarmRequest);
            final DescribeAlarmsRequest describeAlarmsRequest2 = new DescribeAlarmsRequest();
            describeAlarmsRequest2.setProjectId(createProjectResponse.getProjectId());
            describeAlarmsResponse = client.describeAlarms(describeAlarmsRequest2);
            System.out.println("modify alarm success,response:" + modifyAlarmResponse);
            Assert.assertEquals(modifyAlarmRequest.getAlarmName(), describeAlarmsResponse.getAlarms().get(0)
                    .getAlarmName());

            exception = assertThrows(LogException.class, () -> {
                modifyAlarmRequest.setAlarmId("zsq_1243_456");
                client.modifyAlarm(modifyAlarmRequest);
            });
            expectedMessage = "Alarm policy does not exist.";
            actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));

            // delete alarm
            DeleteAlarmRequest deleteAlarmRequest = new DeleteAlarmRequest(createAlarmResponse.getAlarmId());
            DeleteAlarmResponse deleteAlarmResponse = client.deleteAlarm(deleteAlarmRequest);
            assertTrue(deleteAlarmResponse.getRequestId().length() > 0);
            exception = assertThrows(LogException.class, () -> {
                client.deleteAlarm(deleteAlarmRequest);
            });

            expectedMessage = "Alarm policy does not exist.";
            actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));
            System.out.println("delete alarm success,response:" + deleteAlarmResponse);
            // delete alarm notify group
            DeleteAlarmNotifyGroupRequest deleteAlarmNotifyGroupRequest =
                    new DeleteAlarmNotifyGroupRequest(createAlarmNotifyGroupResponse.getAlarmNotifyGroupId());
            DeleteAlarmNotifyGroupResponse deleteAlarmNotifyGroupResponse =
                    client.deleteAlarmNotifyGroup(deleteAlarmNotifyGroupRequest);
            assertTrue(deleteAlarmNotifyGroupResponse.getRequestId().length() > 0);
            exception = assertThrows(LogException.class, () -> {
                client.deleteAlarmNotifyGroup(deleteAlarmNotifyGroupRequest);
            });

            expectedMessage = "Alarm notify group does not exist.";
            actualMessage = exception.getMessage();
            assertTrue(actualMessage.contains(expectedMessage));
            System.out.println("delete alarm notify group success,response:" + deleteAlarmNotifyGroupResponse);
            //delete topic
            DeleteTopicResponse deleteTopicResponse =
                    client.deleteTopic(new DeleteTopicRequest(createTopicResponse.getTopicId()));
            System.out.println("delete topic success,response:" + deleteTopicResponse);
            DeleteProjectResponse deleteProjectResponse = client.deleteProject(new DeleteProjectRequest(projectId));
            System.out.println("delete project success,response:" + deleteProjectResponse);
        } catch (LogException e) {
            e.printStackTrace();
        }
    }
}
