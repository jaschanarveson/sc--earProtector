/* by Jascha Narveson, 2011

This class sets up a synth at the tail of the root node whose sole job is to monitor outgoing volume and to free all nodes if the volume exceeds 1.  This is very handy for coding in private, as it can save you from unpleasantly loud, shocking mistakes (especially if you code with headphones!).

EarProtector will throw up a window asking you if you want it active or not for the current coding session (ie: until you press command-period).  The thinking here is that EarProtector can be part of SC's default set-up by including this in your startup.sc doc.

Assuming that your startup doc sets 's' to the default server, here's the code to enable EarProtector at every server boot and command-period:


s.waitForBoot({  // include these lines in whatever else you have for s.waitForBoot
var ear_protector_func;
ear_protector_func = {EarProtector.new(s)};
CmdPeriod.add(ear_protector_func);
EarProtector.new(s);
});

Note:  it is probably a bad idea to have EarProtector running if you're performing live, as clipping is better than a panic-mute in that case.

EarProtector is currently set up to ask if you want to re-instate it after it detects a clip.

*/




EarProtector {
	classvar surviveCmdPeriod = false;
	var guis;

	*new {^super.new.init}

	*cmdPeriod {EarProtector.new}

	*keepAround {
		surviveCmdPeriod = true;
		CmdPeriod.add(EarProtector);
	}

	*clear {
		surviveCmdPeriod = false;
		CmdPeriod.remove(EarProtector);
	}

	init {
		var task;

		task = Task({
			var monitorSynth, responder;

			"Starting up Ear Protection".postln;

			monitorSynth = SynthDef("ear_protector_synth", {|thresh=1.0, amp=1|
				var sig, ampReading, clipped;
				sig = In.ar(0, 2);
				ampReading = Amplitude.ar(sig);
				clipped = ampReading > thresh;
				SendReply.ar(clipped, "/ear_protector_server_clip_msg");
				ReplaceOut.ar(0, sig * amp);
			}).play(RootNode(Server.default), nil, \addToTail);

			Server.default.sync;


			responder = OSCFunc(
				srcID: Server.default.addr,
				path: "/ear_protector_server_clip_msg",
				func: {
					Server.default.freeAll;
					"ear protector responder".postln;
					{this.make_clip_window}.defer;
				}
			).oneShot;
		});

		guis = ();

		guis.put(\screen, Window.screenBounds);

		guis.put(\winH, 300);

		guis.put(\winW, 600);

		guis.put(\border, 10);

		guis.put(\win,
			Window.new(
				"you want ear protection?",
				Rect(
					(guis[\screen].width/2) - (guis[\winW]/2),
					(guis[\screen].height/2) - (guis[\winH]/2),
					guis[\winW],
					guis[\winH]
				),
				border: false
			);
		);

		guis[\win].view.background = Color.black;

		guis[\win].alpha = 0.98;

		guis.put(\txt, StaticText(guis[\win], Rect(guis[\border]/2, guis[\border]/2, guis[\winW]-guis[\border], guis[\winH]-guis[\border])));

		guis[\txt].background_(Color.grey(0.8, 0.5));

		guis[\txt].font_(Font.new("Monaco", 24));
		guis[\txt].string_("Do you want Ear Protection active?\n\ny = yes\n\nn = no");

		guis[\txt].stringColor_(Color.grey(0.95));

		guis[\txt].align = \center;

		guis[\win].front;

		guis[\win].view.keyDownAction_({|win, char|
			switch(char,
				$y, {
					if (surviveCmdPeriod == false,
						{
							guis[\txt].string_("Do you want Ear Protection to survive Command-Period?\n\ny = yes\n\nn = no");
							guis[\win].view.keyDownAction_({|win, char|
								switch(char,
									$y, {
										Task({
											guis[\txt].string_("Ear Protection will now re-activate on Command-Period");
											1.wait;
											EarProtector.keepAround;
											task.play(AppClock);
											guis[\win].close;
										}).play(AppClock);
									},
									$n, {
										Task({
											guis[\txt].string_("Ear Protection won't re-activate on Command-Period. \n Run 'EarProtector.clear' to change this.");
											1.wait;
											task.play(AppClock);
											guis[\win].close;
										}).play(AppClock);
									}
								)
							});
						},
						{
							task.play(AppClock);
							guis[\win].close;
						}
					);
				},
				$n, {
					Task({
						guis[\txt].string_("\n\nThis session will run without Ear Protection.  Good luck!\n\n");
						1.wait;
						guis[\win].close;
					}).play(AppClock);
				}
			)
		});


	}

	make_clip_window {
		var win, txt;
		win = Window.new(
			"you want ear protection?",
			Rect(
				(guis[\screen].width/2) - (guis[\winW]/2),
				(guis[\screen].height/2) - (guis[\winH]/2),
				guis[\winW],
				guis[\winH]
			),
			border: false
		);

		win.view.background = Color.black;

		win.alpha = 0.98;

		txt = StaticText(
			parent: win,
			bounds: Rect(
				left: guis[\border]/2,
				top: guis[\border]/2,
				width: guis[\winW]-guis[\border],
				height: guis[\winH]-guis[\border]
			)
		);

		txt.background_(Color.grey(0.8, 0.5));

		txt.font_(Font.new("Monaco", 24));
		txt.string_("You're clipping!\n(press any key to continue)");

		txt.stringColor_(Color.grey(0.95));

		txt.align = \center;

		win.view.keyDownAction_({
			win.close;
			EarProtector.new;
		});

		win.front;
	}

}