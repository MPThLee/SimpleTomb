package com.lothrazar.simpletomb.particle;

import com.lothrazar.simpletomb.TombRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ParticleSmokeColumn extends NoRenderParticle {

  private ParticleSmokeColumn(ClientLevel world, double x, double y, double z) {
    super(world, x, y, z);
  }

  @Override
  public void tick() {
    double y = this.y;
    for (int i = 0; i < 6; i++) {
      this.level.addParticle(TombRegistry.ROTATING_SMOKE, this.x - 0.1d, y, this.z - 0.1d, 0d, 0d, 0d);
      this.level.addParticle(TombRegistry.ROTATING_SMOKE, this.x - 0.1d, y, this.z + 0.1d, 0d, 0d, 0d);
      this.level.addParticle(TombRegistry.ROTATING_SMOKE, this.x + 0.1d, y, this.z - 0.1d, 0d, 0d, 0d);
      this.level.addParticle(TombRegistry.ROTATING_SMOKE, this.x + 0.1d, y, this.z + 0.1d, 0d, 0d, 0d);
      y += 0.3d;
    }
    remove();
  }

  public static class Factory implements ParticleProvider<SimpleParticleType> {

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double motionX, double motionY, double motionZ) {
      return new ParticleSmokeColumn(world, x, y, z);
    }
  }
}
