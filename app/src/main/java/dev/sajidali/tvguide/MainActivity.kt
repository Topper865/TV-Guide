package dev.sajidali.tvguide

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.sajidali.demo.databinding.ActivityMainBinding
import dev.sajidali.guide.GuideView
import dev.sajidali.guide.data.Channel
import dev.sajidali.guide.data.DataProvider
import dev.sajidali.guide.data.Event
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val provider = object : DataProvider {

        val events = HashMap<Int, List<Event>>()
        val channels = (0..300).map { position ->
            Channel(position, "Channel $position", "").also {
                generateEvents(position)
            }
        }

        private fun generateEvents(channel: Int) {
            var startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            events[channel] = (0..50).map {
                val endTime =
                    Random.nextLong(startTime, startTime + TimeUnit.HOURS.toMillis(2))
                Event(
                    it,
                    "Event $it",
                    "Description of event $it",
                    startTime,
                    endTime
                ).also {
                    startTime = endTime
                }
            }
        }

        override fun channelAt(position: Int): Channel {
            return channels[position]
        }

        override fun eventsOfChannel(position: Int): Collection<Event> {
            return events[position] ?: emptyList()
        }

        override fun eventOfChannelAt(channel: Int, position: Int): Event? {
            return events[channel]?.get(position)
        }

        override fun size(): Int {
            return 50
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.guideView.apply {
            setDataProvider(provider)
            setEPGClickListener(object : GuideView.ClickListener {
                override fun onChannelClicked(position: Int, channel: Channel?) {
                    // Handle channel click
                    Toast.makeText(
                        this@MainActivity,
                        "Channel ${channel?.title} clicked",
                        Toast.LENGTH_SHORT
                    ).show()

                }

                override fun onEventSelected(channel: Channel?, program: Event?) {
                    // Handle event selection
                    binding.txtChannel.text = channel?.title
                    binding.txtEvent.text = program?.title
                }

                override fun onEventClicked(
                    channelPosition: Int,
                    programPosition: Int,
                    channel: Channel?,
                    program: Event?
                ) {
                    // Handle event click
                    Toast.makeText(
                        this@MainActivity,
                        "Event ${program?.title} clicked",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onEventLongClicked(
                    channelPosition: Int,
                    programPosition: Int,
                    channel: Channel?,
                    program: Event?
                ) {
                    // Handle event long click
                    Toast.makeText(
                        this@MainActivity,
                        "Event ${program?.title} long clicked",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            })
        }

    }


}