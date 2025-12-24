package com.illiad.proxy;

import com.illiad.proxy.codec.v5.V5InitReqDecoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.v5.V5CommandHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StarterTest {

    @Mock
    private Params params;
    @Mock
    private V5ServerEncoder v5ServerEncoder;
    @Mock
    private V5CommandHandler v5CommandHandler;

    private Starter starter;

    @BeforeEach
    void setUp() {
        when(params.getLocalPort()).thenReturn(8080);
        // Build ParamBus with minimal mocks so Starter constructor invokes params.getLocalPort()
        HandlerNamer namer = mock(HandlerNamer.class);
        starter = new Starter(new ParamBus(params, namer, v5ServerEncoder, null, null, null, null, null, null, null));
    }

    @Test
    @Disabled("Temporarily disabled while cleaning up doc/test changes")
    void testStarterConfiguration() {
        // Verify that the Params bean's getLocalPort method is called during construction
        verify(params).getLocalPort();
        // The rest of the Netty pipeline wiring is tested in integration tests; keep unit test focused
        assertNotNull(starter);
    }

}