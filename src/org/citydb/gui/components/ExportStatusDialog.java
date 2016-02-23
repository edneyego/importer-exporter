/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 * 
 * (C) 2013 - 2016,
 * Chair of Geoinformatics,
 * Technische Universitaet Muenchen, Germany
 * http://www.gis.bgu.tum.de/
 * 
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 * 
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Muenchen <http://www.moss.de/>
 * 
 * The 3D City Database Importer/Exporter program is free software:
 * you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 */
package org.citydb.gui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.citydb.api.event.Event;
import org.citydb.api.event.EventDispatcher;
import org.citydb.api.event.EventHandler;
import org.citydb.api.registry.ObjectRegistry;
import org.citydb.config.language.Language;
import org.citydb.modules.common.event.CounterEvent;
import org.citydb.modules.common.event.CounterType;
import org.citydb.modules.common.event.EventType;
import org.citydb.modules.common.event.StatusDialogMessage;
import org.citydb.modules.common.event.StatusDialogProgressBar;
import org.citydb.modules.common.event.StatusDialogTitle;
import org.citydb.util.gui.GuiUtil;

@SuppressWarnings("serial")
public class ExportStatusDialog extends JDialog implements EventHandler {
	private final EventDispatcher eventDispatcher;

	private JLabel fileName;
	private JLabel tileLabel;
	private JLabel messageLabel;
	private JLabel details;
	private JLabel featureLabel;
	private JLabel textureLabel;
	private JPanel main;
	private JPanel row;
	private JLabel tileCounterLabel;
	private JLabel featureCounterLabel;
	private JLabel textureCounterLabel;
	private JProgressBar progressBar;
	public JButton cancelButton;
	private long featureCounter;
	private long textureCounter;
	private volatile boolean acceptStatusUpdate = true;

	private int totalTileAmount;

	public ExportStatusDialog(JFrame frame, 
			String impExpTitle,
			String impExpMessage,
			int totalTileAmount) {
		super(frame, impExpTitle, true);

		eventDispatcher = ObjectRegistry.getInstance().getEventDispatcher();
		eventDispatcher.addEventHandler(EventType.COUNTER, this);
		eventDispatcher.addEventHandler(EventType.STATUS_DIALOG_PROGRESS_BAR, this);
		eventDispatcher.addEventHandler(EventType.STATUS_DIALOG_MESSAGE, this);
		eventDispatcher.addEventHandler(EventType.STATUS_DIALOG_TITLE, this);
		eventDispatcher.addEventHandler(EventType.INTERRUPT, this);

		this.totalTileAmount = totalTileAmount;

		initGUI(impExpTitle, impExpMessage);
	}

	private void initGUI(String impExpTitle, String impExpMessage) {
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		fileName = new JLabel(impExpMessage);
		fileName.setFont(fileName.getFont().deriveFont(Font.BOLD));
		messageLabel = new JLabel(" ");
		cancelButton = new JButton(Language.I18N.getString("common.button.cancel"));
		featureLabel = new JLabel(Language.I18N.getString("common.status.dialog.featureCounter"));
		textureLabel = new JLabel(Language.I18N.getString("common.status.dialog.textureCounter"));

		featureCounterLabel = new JLabel("0", SwingConstants.TRAILING);
		textureCounterLabel = new JLabel("0", SwingConstants.TRAILING);
		featureCounterLabel.setPreferredSize(new Dimension(100, featureLabel.getPreferredSize().height));
		textureCounterLabel.setPreferredSize(new Dimension(100, textureLabel.getPreferredSize().height));

		progressBar = new JProgressBar();

		setLayout(new GridBagLayout()); 
		{			
			main = new JPanel();
			add(main, GuiUtil.setConstraints(0,0,1.0,0.0,GridBagConstraints.BOTH,5,5,5,5));
			main.setLayout(new GridBagLayout());
			{
				int gridY = 0;

				main.add(fileName, GuiUtil.setConstraints(0,gridY++,0.0,0,GridBagConstraints.HORIZONTAL,5,5,5,5));
				main.add(messageLabel, GuiUtil.setConstraints(0,gridY++,0.0,0,GridBagConstraints.HORIZONTAL,5,5,0,5));
				main.add(progressBar, GuiUtil.setConstraints(0,gridY++,1.0,0.0,GridBagConstraints.HORIZONTAL,0,5,5,5));

				details = new JLabel("Details");
				main.add(details, GuiUtil.setConstraints(0,gridY++,1.0,0.0,GridBagConstraints.HORIZONTAL,5,5,0,5));

				row = new JPanel();
				row.setBackground(new Color(255, 255, 255));
				row.setBorder(BorderFactory.createEtchedBorder());
				main.add(row, GuiUtil.setConstraints(0,gridY++,1.0,0.0,GridBagConstraints.BOTH,0,5,5,5));
				row.setLayout(new GridBagLayout());
				{
					row.add(featureLabel, GuiUtil.setConstraints(0,0,0.0,0.0,GridBagConstraints.HORIZONTAL,5,5,1,5));
					row.add(featureCounterLabel, GuiUtil.setConstraints(1,0,1.0,0.0,GridBagConstraints.HORIZONTAL,5,5,1,5));
					row.add(textureLabel, GuiUtil.setConstraints(0,1,0.0,0.0,GridBagConstraints.HORIZONTAL,1,5,5,5));
					row.add(textureCounterLabel, GuiUtil.setConstraints(1,1,1.0,0.0,GridBagConstraints.HORIZONTAL,1,5,5,5));

					if (totalTileAmount > 0) {
						tileLabel = new JLabel(Language.I18N.getString("common.status.dialog.tileCounter"));
						tileCounterLabel = new JLabel("0", SwingConstants.TRAILING);
						tileCounterLabel.setPreferredSize(new Dimension(100, tileCounterLabel.getPreferredSize().height));

						row.add(tileLabel, GuiUtil.setConstraints(0,2,0.0,0.0,GridBagConstraints.HORIZONTAL,1,5,5,5));
						row.add(tileCounterLabel, GuiUtil.setConstraints(1,2,1.0,0.0,GridBagConstraints.HORIZONTAL,1,5,5,5));
					}
				}
			}

			add(cancelButton, GuiUtil.setConstraints(0,1,0.0,0.5,GridBagConstraints.NONE,5,5,5,5));
		}

		pack();
		progressBar.setIndeterminate(true);

		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				eventDispatcher.removeEventHandler(ExportStatusDialog.this);
			}
		});
	}

	public JButton getCancelButton() {
		return cancelButton;
	}

	@Override
	public void handleEvent(Event e) throws Exception {

		if (e.getEventType() == EventType.COUNTER &&
				((CounterEvent)e).getType() == CounterType.TOPLEVEL_FEATURE) {
			featureCounter += ((CounterEvent)e).getCounter();
			featureCounterLabel.setText(String.valueOf(featureCounter));
		}

		else if (e.getEventType() == EventType.COUNTER &&
				((CounterEvent)e).getType() == CounterType.TEXTURE_IMAGE) {
			textureCounter += ((CounterEvent)e).getCounter();
			textureCounterLabel.setText(String.valueOf(textureCounter));
		}

		else if (e.getEventType() == EventType.INTERRUPT) {
			acceptStatusUpdate = false;
			messageLabel.setText(Language.I18N.getString("common.dialog.msg.abort"));
			progressBar.setIndeterminate(true);
		}

		else if (e.getEventType() == EventType.STATUS_DIALOG_PROGRESS_BAR && acceptStatusUpdate) {		
			if (((StatusDialogProgressBar)e).isSetIntermediate()) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {		
						if (!progressBar.isIndeterminate())
							progressBar.setIndeterminate(true);
					}
				});

				return;
			}

			if (progressBar.isIndeterminate()) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						progressBar.setIndeterminate(false);
					}
				});
			} 

			int max = ((StatusDialogProgressBar)e).getMaxValue();
			int current = ((StatusDialogProgressBar)e).getCurrentValue();

			if (max != progressBar.getMaximum())
				progressBar.setMaximum(max);
			progressBar.setValue(current);
		}

		else if (e.getEventType() == EventType.STATUS_DIALOG_MESSAGE && acceptStatusUpdate) {
			messageLabel.setText(((StatusDialogMessage)e).getMessage());
		}

		else if (e.getEventType() == EventType.STATUS_DIALOG_TITLE && acceptStatusUpdate) {
			fileName.setText(((StatusDialogTitle)e).getTitle());
			if (totalTileAmount > 0) {
				tileCounterLabel.setText(String.valueOf(--totalTileAmount));
			}
		}
	}
}
