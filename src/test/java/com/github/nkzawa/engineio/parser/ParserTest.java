package com.github.nkzawa.engineio.parser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.github.nkzawa.engineio.parser.Parser.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class ParserTest {

    static final String ERROR_DATA = "parser error";

    @Test
    public void encodeAsString() {
        assertThat(encodePacket(new Packet(Packet.MESSAGE, "test")), isA(String.class));
    }

    @Test
    public void decodeAsPacket() {
        assertThat(decodePacket(encodePacket(new Packet(Packet.MESSAGE, "test"))), isA(Packet.class));
    }

    @Test
    public void noData() {
        Packet p = decodePacket(encodePacket(new Packet(Packet.MESSAGE)));
        assertThat(p.type, is(Packet.MESSAGE));
        assertThat(p.data, is(nullValue()));
    }

    @Test
    public void encodeOpenPacket() {
        Packet p = decodePacket(encodePacket(new Packet(Packet.OPEN, "{\"some\":\"json\"}")));
        assertThat(p.type, is(Packet.OPEN));
        assertThat(p.data, is("{\"some\":\"json\"}"));
    }

    @Test
    public void encodeClosePacket() {
        Packet p = decodePacket(encodePacket(new Packet(Packet.CLOSE)));
        assertThat(p.type, is(Packet.CLOSE));
    }

    @Test
    public void encodePingPacket() {
        Packet p = decodePacket(encodePacket(new Packet(Packet.PING, "1")));
        assertThat(p.type, is(Packet.PING));
        assertThat(p.data, is("1"));
    }

    @Test
    public void encodePongPacket() {
        Packet p = decodePacket(encodePacket(new Packet(Packet.PONG, "1")));
        assertThat(p.type, is(Packet.PONG));
        assertThat(p.data, is("1"));
    }

    @Test
    public void encodeMessagePacket() {
        Packet p = decodePacket(encodePacket(new Packet(Packet.MESSAGE, "aaa")));
        assertThat(p.type, is(Packet.MESSAGE));
        assertThat(p.data, is("aaa"));
    }

    @Test
    public void encodeUpgradePacket() {
        Packet p = decodePacket(encodePacket(new Packet(Packet.UPGRADE)));
        assertThat(p.type, is(Packet.UPGRADE));
    }

    @Test
    public void encodingFormat() {
        assertThat(encodePacket(new Packet(Packet.MESSAGE, "test")).matches("[0-9].*"), is(true));
        assertThat(encodePacket(new Packet(Packet.MESSAGE)).matches("[0-9]"), is(true));
    }

    @Test
    public void decodeBadFormat() {
        Packet p = decodePacket(":::");
        assertThat(p.type, is(Packet.ERROR));
        assertThat(p.data, is(ERROR_DATA));
    }

    @Test
    public void decodeInexistentTypes() {
        Packet p = decodePacket("94103");
        assertThat(p.type, is(Packet.ERROR));
        assertThat(p.data, is(ERROR_DATA));
    }

    @Test
    public void encodePayloadsAsString() {
        assertThat(encodePayload(new Packet[] {
                new Packet(Packet.PING), new Packet(Packet.PONG)}), isA(String.class));
    }

    @Test
    public void encodeAndDecodePayloads() {
        decodePayload(encodePayload(new Packet[] {new Packet(Packet.MESSAGE, "a")}),
                new DecodePayloadCallback() {
                    @Override
                    public boolean call(Packet packet, int index, int total) {
                        boolean isLast = index + 1 == total;
                        assertThat(isLast, is(true));
                        return true;
                    }
                });
        decodePayload(encodePayload(new Packet[] {
                new Packet(Packet.MESSAGE, "a"), new Packet(Packet.PING)}),
                new DecodePayloadCallback() {
                    @Override
                    public boolean call(Packet packet, int index, int total) {
                        boolean isLast = index + 1 == total;
                        if (!isLast) {
                            assertThat(packet.type, is(Packet.MESSAGE));
                        } else {
                            assertThat(packet.type, is(Packet.PING));
                        }
                        return true;
                    }
                });
    }

    @Test
    public void encodeAndDecodeEmptyPayloads() {
        decodePayload(encodePayload(new Packet[] {}), new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                assertThat(packet.type, is(Packet.OPEN));
                boolean isLast = index + 1 == total;
                assertThat(isLast, is(true));
                return true;
            }
        });
    }

    @Test
    public void decodePayloadBadFormat() {
        decodePayload("1!", new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                boolean isLast = index + 1 == total;
                assertThat(packet.type, is(Packet.ERROR));
                assertThat(packet.data, is(ERROR_DATA));
                assertThat(isLast, is(true));
                return true;
            }
        });
        decodePayload("", new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                boolean isLast = index + 1 == total;
                assertThat(packet.type, is(Packet.ERROR));
                assertThat(packet.data, is(ERROR_DATA));
                assertThat(isLast, is(true));
                return true;
            }
        });
        decodePayload("))", new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                boolean isLast = index + 1 == total;
                assertThat(packet.type, is(Packet.ERROR));
                assertThat(packet.data, is(ERROR_DATA));
                assertThat(isLast, is(true));
                return true;
            }
        });
    }

    @Test
    public void decodePayloadBadLength() {
        decodePayload("1:", new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                boolean isLast = index + 1 == total;
                assertThat(packet.type, is(Packet.ERROR));
                assertThat(packet.data, is(ERROR_DATA));
                assertThat(isLast, is(true));
                return true;
            }
        });
    }

    @Test
    public void decodePayloadBadPacketFormat() {
        decodePayload("3:99:", new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                boolean isLast = index + 1 == total;
                assertThat(packet.type, is(Packet.ERROR));
                assertThat(packet.data, is(ERROR_DATA));
                assertThat(isLast, is(true));
                return true;
            }
        });
        decodePayload("1:aa", new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                boolean isLast = index + 1 == total;
                assertThat(packet.type, is(Packet.ERROR));
                assertThat(packet.data, is(ERROR_DATA));
                assertThat(isLast, is(true));
                return true;
            }
        });
        decodePayload("1:a2:b", new DecodePayloadCallback() {
            @Override
            public boolean call(Packet packet, int index, int total) {
                boolean isLast = index + 1 == total;
                assertThat(packet.type, is(Packet.ERROR));
                assertThat(packet.data, is(ERROR_DATA));
                assertThat(isLast, is(true));
                return true;
            }
        });
    }
}
