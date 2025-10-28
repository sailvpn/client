package com.illiad.proxy;

import com.illiad.proxy.codec.v5.V5ServerEncoder;
import com.illiad.proxy.config.Params;
import com.illiad.proxy.handler.v5.VersionHandler;
import com.illiad.proxy.handler.v5.V5CommandHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.ReflectionSupport;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class StarterTest {

    @Mock
    private Params params;
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
    void testStarterConfiguration() {
        // Verify that the Params bean's getLocalPort method is called
        verify(params).getLocalPort();
        // Mock a pipeline and verify the handlers
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        SocketChannel channel = mock(SocketChannel.class);
        when(channel.pipeline()).thenReturn(pipeline);
        try {
            ReflectionSupport.invokeMethod(ChannelInitializer.class.getMethod("initChannel"), channel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        verify(pipeline).addLast(any(LoggingHandler.class), any(VersionHandler.class));
    }

}