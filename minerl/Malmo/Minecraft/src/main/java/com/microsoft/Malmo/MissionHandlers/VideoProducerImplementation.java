// --------------------------------------------------------------------------------------------------
//  Copyright (c) 2016 Microsoft Corporation
//  
//  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
//  associated documentation files (the "Software"), to deal in the Software without restriction,
//  including without limitation the rights to use, copy, modify, merge, publish, distribute,
//  sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions:
//  
//  The above copyright notice and this permission notice shall be included in all copies or
//  substantial portions of the Software.
//  
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
//  NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
//  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
//  DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// --------------------------------------------------------------------------------------------------

package com.microsoft.Malmo.MissionHandlers;

import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glGetFloat;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX;
import static org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.microsoft.Malmo.MissionHandlerInterfaces.IVideoProducer;
import com.microsoft.Malmo.Schemas.MissionInit;
import com.microsoft.Malmo.Schemas.VideoProducer;

public class VideoProducerImplementation extends HandlerBase implements IVideoProducer
{
    private VideoProducer videoParams;
    private Framebuffer fbo;
    private FloatBuffer depthBuffer;

    @Override
    public boolean parseParameters(Object params)
    {
        if (params == null || !(params instanceof VideoProducer))
            return false;
        this.videoParams = (VideoProducer) params;

        return true;
    }

    @Override
    public VideoType getVideoType()
    {
        return VideoType.VIDEO;
    }

    @Override
    public void getFrame(MissionInit missionInit, ByteBuffer buffer)
    {
        if (!this.videoParams.isWantDepth())
        {
            getRGBFrame(buffer); // Just return the simple RGB, 3bpp image.
            return;
        }

        // Otherwise, do the work of extracting the depth map:
        final int width = this.videoParams.getWidth();
        final int height = this.videoParams.getHeight();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, Minecraft.getMinecraft().getFramebuffer().framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.fbo.framebufferObject);
        GL30.glBlitFramebuffer(0, 0, Minecraft.getMinecraft().getFramebuffer().framebufferWidth, Minecraft.getMinecraft().getFramebuffer().framebufferHeight, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        this.fbo.bindFramebuffer(true);
        glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, buffer);
        glReadPixels(0, 0, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, this.depthBuffer);
        this.fbo.unbindFramebuffer();
 
        // TODO only works for case when width * height % 4 == 0
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        int offset = width * height * 3 / 4;
        for (int i = 0; i < width * height; i++)
        {
            float f = this.depthBuffer.get(i);
            floatBuffer.put(offset + i, f);
        }
        // Reset depth buffer ready for next read:
        this.depthBuffer.clear();

        // Write Modelview and Projection matrices
        offset += width * height;
        FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
        glGetFloat(GL_MODELVIEW_MATRIX, modelview);
        for (int i = 0; i < 16; i++) {
            floatBuffer.put(offset + i, modelview.get(i));
        }

        offset += 16;
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        glGetFloat(GL_PROJECTION_MATRIX, projection);        
        for (int i = 0; i < 16; i++) {
            floatBuffer.put(offset + i, projection.get(i));
        }
    }

    @Override
    public int getWidth()
    {
        return this.videoParams.getWidth();
    }

    @Override
    public int getHeight()
    {
        return this.videoParams.getHeight();
    }

    public int getRequiredBufferSize()
    {
        int nPixels = this.videoParams.getWidth() * this.videoParams.getHeight();
        int size = nPixels * 3;
        if (this.videoParams.isWantDepth()) {
            size += nPixels * 4; // Float precision depth
            size += 2 * 16 * 4; // Modelview and Projection matrices
        }
        return size;
    }

    private void getRGBFrame(ByteBuffer buffer)
    {
        final int format = GL_RGB;
        final int width = this.videoParams.getWidth();
        final int height = this.videoParams.getHeight();

        // Render the Minecraft frame into our own FBO, at the desired size:
        this.fbo.bindFramebuffer(true);
        Minecraft.getMinecraft().getFramebuffer().framebufferRenderExt(width, height, true);
        // Now read the pixels out from that:
        // glReadPixels appears to be faster than doing:
        // GlStateManager.bindTexture(this.fbo.framebufferTexture);
        // GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, format, GL_UNSIGNED_BYTE,
        // buffer);
        glReadPixels(0, 0, width, height, format, GL_UNSIGNED_BYTE, buffer);
        this.fbo.unbindFramebuffer();
        GlStateManager.enableDepth();
        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
    }

    @Override
    public void prepare(MissionInit missionInit)
    {
        this.fbo = new Framebuffer(this.videoParams.getWidth(), this.videoParams.getHeight(), true);
        // Create a buffer for retrieving the depth map, if requested:
        if (this.videoParams.isWantDepth())
            this.depthBuffer = BufferUtils.createFloatBuffer(this.videoParams.getWidth() * this.videoParams.getHeight());
        // Set the requested camera position
        Minecraft.getMinecraft().gameSettings.thirdPersonView = this.videoParams.getViewpoint();
    }

    @Override
    public void cleanup()
    {
        this.fbo.deleteFramebuffer(); // Must do this or we leak resources.
    }
}
