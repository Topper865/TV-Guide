package dev.sajidali.guide.data

interface DataProvider {

    fun channelAt(position: Int): Channel

    fun eventsOfChannel(position: Int): Collection<Event>

    fun eventOfChannelAt(channel: Int, position: Int): Event?

    fun size(): Int

}