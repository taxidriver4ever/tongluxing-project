package com.tongluxing.chat.callback;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TencentImCallbackControllerTest {

    @Test
    void notificationCallbackVerifiesThenSubmitsAndImmediatelyReturnsAck() {
        TencentImCallbackService service = mock(TencentImCallbackService.class);
        TencentImCallbackController controller = new TencentImCallbackController(service);
        Map<String, Object> body = Map.of("MsgKey", "msg-1");
        Map<String, Object> ack = Map.of("ActionStatus", "OK", "ErrorCode", 0);
        when(service.isBlockingCallback("Group.CallbackAfterSendMsg")).thenReturn(false);
        when(service.allow()).thenReturn(ack);

        Map<String, Object> result = controller.callback(
                1L, "Group.CallbackAfterSendMsg", 2L, "sign", body);

        assertSame(ack, result);
        InOrder order = inOrder(service);
        order.verify(service).verify(1L, 2L, "sign");
        order.verify(service).isBlockingCallback("Group.CallbackAfterSendMsg");
        order.verify(service).submitAsync("Group.CallbackAfterSendMsg", body);
        order.verify(service).allow();
    }

    @Test
    void blockingCallbackReturnsSynchronousDecisionWithoutAsyncSubmission() {
        TencentImCallbackService service = mock(TencentImCallbackService.class);
        TencentImCallbackController controller = new TencentImCallbackController(service);
        Map<String, Object> body = Map.of("MsgKey", "msg-2");
        Map<String, Object> reject = Map.of("ActionStatus", "OK", "ErrorCode", 1);
        when(service.isBlockingCallback("Group.CallbackBeforeSendMsg")).thenReturn(true);
        when(service.handleBlocking("Group.CallbackBeforeSendMsg", body)).thenReturn(reject);

        Map<String, Object> result = controller.callback(
                1L, "Group.CallbackBeforeSendMsg", 2L, "sign", body);

        assertSame(reject, result);
        verify(service).handleBlocking("Group.CallbackBeforeSendMsg", body);
        verify(service, never()).submitAsync("Group.CallbackBeforeSendMsg", body);
    }
}
