package com.illiad.proxy;

import com.illiad.proxy.codec.v4.V4ServerEncoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.v4.V4CommandHandler;
import com.illiad.proxy.handler.v5.V5CommandHandler;
import io.netty.channel.ChannelInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


import static org.mockito.Mockito.*;

class StarterTest {

    private Params params;
    private V4ServerEncoder v4ServerEncoder;
    private V4CommandHandler v4CommandHandler;
    private V5ServerEncoder v5ServerEncoder;
    private V5CommandHandler v5CommandHandler;

    @BeforeEach
    void setUp() {
        params = mock(Params.class);
        v4ServerEncoder = mock(V4ServerEncoder.class);
        v4CommandHandler = mock(V4CommandHandler.class);
        v5ServerEncoder = mock(V5ServerEncoder.class);
        v5CommandHandler = mock(V5CommandHandler.class);

        when(params.getLocalPort()).thenReturn(8080);
    }

    @Test
    void testStarter() {
        Starter starter = new Starter(params, v4ServerEncoder, v4CommandHandler, v5ServerEncoder, v5CommandHandler);

        // Verify that the server is configured correctly
        ArgumentCaptor captor = ArgumentCaptor.forClass(ChannelInitializer.class);
        verify(params).getLocalPort();
        verifyNoMoreInteractions(params, v4ServerEncoder, v4CommandHandler, v5ServerEncoder, v5CommandHandler);
    }
}