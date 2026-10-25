package com.panda.fullcourse.food;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.panda.fullcourse.logic.Family;
import com.panda.fullcourse.logic.Tier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

/** A profile as the server sends it, and as the client reads what it's sent. */
class ProfileTest {
    @Test
    void aProfileSyncsWhole() {
        Profile p = new Profile(Tier.GOURMET, 9, 3, 4, 5, List.of(Family.EMBER, Family.BRAWN, Family.FORTITUDE), false, true);
        ByteBuf buf = Unpooled.buffer();
        Profile.STREAM_CODEC.encode(buf, p);
        assertEquals(p, Profile.STREAM_CODEC.decode(buf));
    }

    /** A family sent twice is read once; a family number past the last is read as the last; every one sent is read. */
    @Test
    void theClientReadsEveryFamilySentEachOnce() {
        ByteBuf buf = Unpooled.buffer();
        for (int v : new int[] {Tier.HEARTY.stars(), 5, 2, 2, 3}) ByteBufCodecs.VAR_INT.encode(buf, v);
        int[] families = {Family.VIGOR.ordinal(), Family.VIGOR.ordinal(), 99, Family.SWIFT.ordinal(), Family.BRAWN.ordinal(),
                Family.TIDE.ordinal(), Family.FORTUNE.ordinal(), Family.EMBER.ordinal(), Family.LIGHTFOOT.ordinal(),
                Family.FORTITUDE.ordinal(), Family.DILIGENCE.ordinal(), Family.NIGHT_EYE.ordinal()};
        ByteBufCodecs.VAR_INT.encode(buf, families.length);
        for (int f : families) ByteBufCodecs.VAR_INT.encode(buf, f);
        ByteBufCodecs.BOOL.encode(buf, true);
        ByteBufCodecs.BOOL.encode(buf, false);
        Profile p = Profile.STREAM_CODEC.decode(buf);
        assertEquals(List.of(Family.VIGOR, Family.MENDING, Family.SWIFT, Family.BRAWN, Family.TIDE, Family.FORTUNE, Family.EMBER,
                Family.LIGHTFOOT, Family.FORTITUDE, Family.DILIGENCE, Family.NIGHT_EYE), p.families());
        assertEquals(true, p.remedy(), "the flags after the families are read in place");
        assertEquals(0, buf.readableBytes(), "the whole message is read");
    }

    @Test
    void aProfileListsEachFamilyOnce() {
        assertEquals(List.of(Family.VIGOR, Family.BRAWN),
                new Profile(Tier.PREPARED, 3, 1, 1, 2, List.of(Family.VIGOR, Family.BRAWN, Family.VIGOR), false, false).families());
    }
}
