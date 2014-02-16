package org.unbiquitous.ubiengine.engine.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListResourceBundle;

import org.unbiquitous.ubiengine.engine.input.InputManager;
import org.unbiquitous.ubiengine.engine.input.keyboard.KeyboardReceptionDriver;
import org.unbiquitous.ubiengine.engine.time.DeltaTime;
import org.unbiquitous.ubiengine.util.ComponentContainer;
import org.unbiquitous.ubiengine.util.Logger;
import org.unbiquitous.uos.core.UOS;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.applicationManager.UosApplication;
import org.unbiquitous.uos.core.ontologyEngine.api.OntologyDeploy;
import org.unbiquitous.uos.core.ontologyEngine.api.OntologyStart;
import org.unbiquitous.uos.core.ontologyEngine.api.OntologyUndeploy;
import org.unbiquitous.uos.network.socket.connectionManager.TCPConnectionManager;
import org.unbiquitous.uos.network.socket.radar.PingRadar;

/**
 * The game class. Extend it only to implement getSettings().
 * @author Pimenta
 *
 */
public abstract class UbiGame implements UosApplication {
  /**
   * Must be implemented by the game class.
   * @return Reference to the game initial settings.
   */
  protected abstract Settings getSettings();
  
  /**
   * Just a "typedef" for HashMap{@literal <}String, Object{@literal >}.
   * @author Pimenta
   *
   */
  @SuppressWarnings("serial")
  public static class Settings extends HashMap<String, Object> {
    private Settings validate() {
      if (get("root_path") == null)
        put("root_path", ".");
      if (get("window_title") == null)
        put("window_title", "UbiEngine");
      if (get("window_width") == null)
        put("window_width", 1280);
      if (get("window_height") == null)
        put("window_height", 720);
      if (get("first_state") == null)
        throw new Error("First game state not defined!");
      return this;
    }
  }
  
  /**
   * Use this method in main() to start the game.
   * @param ubiGame Class{@literal <}?{@literal >} that extends UosGame.
   */
  protected static void run(final Class<? extends UbiGame> ubiGame) {
    new UOS().init(new ListResourceBundle() {
      protected Object[][] getContents() {
        return new Object[][] {
          {"ubiquitos.connectionManager", TCPConnectionManager.class.getName()},
          {"ubiquitos.radar", PingRadar.class.getName()},
          {"ubiquitos.eth.tcp.port", "14984"},
          {"ubiquitos.eth.tcp.passivePortRange", "14985-15000"},
          {"ubiquitos.uos.deviceName","compDevice"},
          {"ubiquitos.driver.deploylist", KeyboardReceptionDriver.class.getName()},
          {"ubiquitos.application.deploylist", ubiGame.getName()}
        };
      }
    });
  }
  
  /**
   * Call to change the current game state.
   * @param state New game state.
   */
  public void change(GameState state) {
    if (state == null)
      throw new Error("Trying to change to null GameState!");
    state_change = state;
    change_option = ChangeOption.CHANGE;
  }
  
  /**
   * Call to push a game state.
   * @param state Game state to be pushed.
   */
  public void push(GameState state) {
    if (state == null)
      throw new Error("Trying to push null GameState!");
    state_change = state;
    change_option = ChangeOption.PUSH;
  }
  
  /**
   * Call to pop the current game state.
   * @param args Arguments to be passed to the new current game state.
   */
  public void pop(Object... args) {
    pop_args = args;
    change_option = ChangeOption.POP;
  }
  
  /**
   * Call to shutdown.
   */
  public void quit() {
    change_option = ChangeOption.QUIT;
  }
//==============================================================================
//nothings else matters from here to below
//==============================================================================
  private String rootpath = ".";
  private ComponentContainer components = Components.get();
  private List<InputManager> managers = new ArrayList<InputManager>();
  private LinkedList<GameState> states = new LinkedList<GameState>();
  private DeltaTime deltatime = null;
  private Screen screen = null;
  
  private enum ChangeOption {
    NA,
    CHANGE,
    PUSH,
    POP,
    QUIT
  }
  
  private GameState state_change = null;
  private Object[] pop_args = null;
  private ChangeOption change_option = ChangeOption.NA;
  
  /**
   * uOS's private use.
   */
  public void start(Gateway gateway, OntologyStart ontology) {
    try {
      init(gateway);
      while (states.size() > 0) {
        deltatime.start();
        for (InputManager im : managers)
          im.update();
        for (GameState gs : states)
          gs.update();
        for (GameState gs : states)
          gs.render();
        screen.update(deltatime.getRealDT());
        checkStateChange();
        deltatime.finish();
      }
    } catch (Error e) {
      Logger.log(e, rootpath + "/ErrorLog.txt");
    } catch (Exception e) {
      Logger.log(new Error(e), rootpath + "/ErrorLog.txt");
    }
    if (screen != null)
      screen.close();
  }
  
  /**
   * uOS's private use.
   */
  public void stop() {
    
  }
  
  /**
   * uOS's private use.
   */
  public void init(OntologyDeploy ontology, String appId) {
    
  }
  
  /**
   * uOS's private use.
   */
  public void tearDown(OntologyUndeploy ontology) {
    
  }
  
  @SuppressWarnings("unchecked")
  private void init(Gateway gateway) throws Exception {
    Settings settings = getSettings().validate();
    rootpath = (String)settings.get("root_path");
    components.put(Settings.class, settings);
    
    components.put(UbiGame.class, this);
    
    components.put(Gateway.class, gateway);
    
    components.put(DeltaTime.class, deltatime = new DeltaTime());
    
    components.put(Screen.class, screen = new Screen(
        (String)settings.get("window_title"),
        ((Integer)settings.get("window_width")).intValue(),
        ((Integer)settings.get("window_height")).intValue()
    ));
    
    List<Class<?>> ims = (List<Class<?>>)settings.get("input_managers");
    if (ims != null) {
      for (Class<?> im : ims) {
        managers.add((InputManager)components.put(im, im
          .getConstructor(ComponentContainer.class).newInstance(components)
        ));
      }
    }
    
    states.add(((GameState)((Class<?>)settings.get("first_state")).newInstance()));
  }
  
  private void checkStateChange() {
    switch (change_option) {
      case NA:
        break;
      
      case CHANGE:
        states.removeLast().close();
        states.add(state_change);
        break;
        
      case PUSH:
        states.add(state_change);
        break;
        
      case POP:
        states.removeLast().close();
        if (states.size() > 0)
          states.getLast().wakeup(pop_args);
        break;
        
      case QUIT:
        states.clear();
        break;
        
      default:
        throw new Error("Invalid value for ChangeOption in UosGame!");
    }
    state_change = null;
    pop_args = null;
    change_option = ChangeOption.NA;
  }
}