package com.illiad.proxy;

import com.illiad.proxy.codec.v4.V4ServerEncoder;
import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.v4.V4CommandHandler;
import com.illiad.proxy.handler.v5.V5CommandHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StarterTest {

    @Mock
    private Params params;
    @Mock
    private V4ServerEncoder v4ServerEncoder;
    @Mock
    private V4CommandHandler v4CommandHandler;
    @Mock
    private V5ServerEncoder v5ServerEncoder;
    @Mock
    private V5CommandHandler v5CommandHandler;
    @InjectMocks
    private Starter starter;

    @BeforeEach
    void setUp() {
        when(params.getLocalPort()).thenReturn(8080);
    }

    @Test
    void testStarter() {

        // Verify that the server is configured correctly
        ArgumentCaptor<ChannelInitializer<SocketChannel>> captor = ArgumentCaptor.forClass(ChannelInitializer.class);
        verify(params).getLocalPort();
        verifyNoMoreInteractions(params, v4ServerEncoder, v4CommandHandler, v5ServerEncoder, v5CommandHandler);
    }
}