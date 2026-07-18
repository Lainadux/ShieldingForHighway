import warnings
import os
import sys
import json

import gymnasium as gym
import highway_env
import numpy as np
import torch as th
from stable_baselines3 import DQN
from stable_baselines3.common.callbacks import CheckpointCallback

import time
save_base_path = "base"
ADVERSARIAL_SAVE_BASE_PATH = "adversarial"

BASE_ENV_CONFIG = {
    "action": {
        "type": "DiscreteMetaAction",
        "target_speeds": [0, 5, 10, 15, 20, 25, 30, 35, 40]},
    "lanes_count": 3,  # (4)
    "collision_reward": -1,  # (-1)
    "right_lane_reward": 0.1,  # (0.1),
    "reward_speed_range": [0, 40],
    "high_speed_reward": 1,
}


def create_base_env(render_mode='human'):
    env = gym.make("highway-fast-v0", render_mode=render_mode)
    env.configure(BASE_ENV_CONFIG)
    env.reset()
    return env


class BaseAIDriver:
    save_base_path = "base"
    profile_name = "base"

    def __init__(self, model_path=None):
        _, default_model_path = get_paths(self.save_base_path, "new")
        self.model_path = model_path or default_model_path
        self.env = create_base_env(render_mode=None)
        self.action_map = {0: "LANE_LEFT", 1: "IDLE", 2: "LANE_RIGHT", 3: "FASTER", 4: "SLOWER"}
        self.model = DQN.load(self.model_path)

    def decide_action(self, vehicles):
        obs = self._preprocess(vehicles)
        action, _states = self.model.predict(obs, deterministic=True)
        action_id = int(action)
        return {
            "action": action_id,
            "action_name": self.action_map.get(action_id, "IDLE"),
        }

    def decide_action_from_safe_actions(self, vehicles, safe_actions):
        safe_actions = [int(action) for action in safe_actions]
        if not safe_actions:
            safe_actions = [4]
        obs = self._preprocess(vehicles)
        obs_tensor, _ = self.model.policy.obs_to_tensor(obs)
        with th.no_grad():
            q_values = self.model.policy.q_net(obs_tensor).detach().cpu().numpy()[0]
        action_id = max(safe_actions, key=lambda action: q_values[action])
        return {
            "action": int(action_id),
            "action_name": self.action_map.get(int(action_id), "IDLE"),
            "safe_actions": safe_actions,
        }

    def _preprocess(self, vehicles):
        from highway_env.vehicle.controller import MDPVehicle
        from highway_env.vehicle.kinematics import Vehicle

        road = self.env.unwrapped.road
        road.vehicles = []
        ego_vehicle = None

        for vehicle in vehicles:
            x = float(vehicle.get("x", 0.0))
            y = float(vehicle.get("y", 0.0))
            vx = float(vehicle.get("vx", vehicle.get("speed", 0.0)))
            heading = float(vehicle.get("heading", 0.0))
            role = str(vehicle.get("role", "NPC")).upper()

            if role == "EGO":
                ego_vehicle = MDPVehicle(road, position=[x, y], heading=heading, speed=vx)
                road.vehicles.append(ego_vehicle)
            else:
                road.vehicles.append(Vehicle(road, position=[x, y], heading=heading, speed=vx))

        if ego_vehicle is None:
            raise ValueError("AI input must contain one vehicle with role='EGO'")

        self.env.unwrapped.vehicle = ego_vehicle
        self.env.unwrapped.controlled_vehicles = [ego_vehicle]
        return self.env.unwrapped.observation_type.observe()


class AdversarialAIDriver(BaseAIDriver):
    save_base_path = ADVERSARIAL_SAVE_BASE_PATH
    profile_name = "adversarial"


def create_ai_driver(profile="base", model_path=None):
    profile = (profile or "base").strip().lower()
    if profile in ("adversarial", "adversary", "aggressive", "ad"):
        return AdversarialAIDriver(model_path)
    if profile in ("base", "normal", "default"):
        return BaseAIDriver(model_path)
    raise ValueError(f"Unknown AI profile: {profile}")


def run_ai_server(profile="base", model_path=None):
    driver = create_ai_driver(profile, model_path)
    print(json.dumps({"ready": True, "profile": driver.profile_name, "model_path": driver.model_path}), flush=True)
    for line in sys.stdin:
        try:
            request = json.loads(line)
            vehicles = request.get("vehicles", request)
            response = driver.decide_action(vehicles)
        except Exception as exc:
            response = {"error": str(exc)}
        print(json.dumps(response), flush=True)


# Configuration (default values in parenthesis)
env = None
# env.configure({
#     "lanes_count": 3,  # (4)
#     "vehicles_count": 40,  # (50)
#     "duration": 40,  # (40) [s]
#
#     # (-1) The reward received when colliding with a vehicle.
#     "collision_reward": -1,
#     # ([20, 30]) [m/s] The reward for high speed is mapped linearly from this range to [0, HighwayEnv.HIGH_SPEED_REWARD].
#     "reward_speed_range": [20, 30],
#     # (0.1) The reward received when driving on the right-most lanes, linearly mapped to
#     "right_lane_reward": 0.2, # (0.1)
#     # zero for other lanes.
#     # (0.4) The reward received when driving at full speed, linearly mapped to zero for
#     "high_speed_reward": 0.5, # (0.4)
#     # lower speeds according to config["reward_speed_range"].
#     # The reward received at each lane change action.
#     "lane_change_reward": 0.1, # (0)
#
#     "simulation_frequency": 15,  # (15) [Hz]
#     "policy_frequency": 1,  # (1) [Hz]
#
#     "normalize_reward": True, # (True)
#     "offroad_terminal": False, # (False)
#
#     # Changes for faster training
#     # cf. https://github.com/Farama-Foundation/HighwayEnv/issues/223
#     "disable_collision_checks": True,
# })

def display_script_help():
    print("Usage: python3 highway_agent.py train [model_id]")
    print("       python3 highway_agent.py test [model_id]")
    print("       python3 highway_agent.py config")
    print()
    print("model_id: The name of the model to save/load (default: 'new')")

def get_paths(base_path=None, model_id=None):
    if base_path is None:
        base_path = save_base_path
    if model_id is not None:
        pass
    elif len(sys.argv) > 2:
        model_id = sys.argv[2]
    else:
        model_id = 'new'

    save_path = os.path.join(base_path, model_id)
    model_path = os.path.join(save_path, "trained_model")

    return save_path, model_path


if __name__ == '__main__':
    if len(sys.argv) >= 2 and sys.argv[1] == 'ai-server':
        profile = sys.argv[2] if len(sys.argv) > 2 else "base"
        model_path = sys.argv[3] if len(sys.argv) > 3 else None
        run_ai_server(profile, model_path)
        sys.exit(0)

    env = create_base_env(render_mode='human')

    if len(sys.argv) < 2:
        display_script_help()
        sys.exit(1)

    if sys.argv[1] == 'train':
        save_path, model_path = get_paths()


        # Settings adapted from
        # https://github.com/Farama-Foundation/HighwayEnv/blob/master/scripts/sb3_highway_dqn.py
        model = DQN('MlpPolicy', env,
                    policy_kwargs=dict(net_arch=[256, 256]),
                    learning_rate=5e-4,
                    buffer_size=15000,
                    learning_starts=200,
                    batch_size=32,
                    gamma=0.9,  # Discount factor
                    exploration_fraction=0.3,
                    exploration_initial_eps=1.0,
                    exploration_final_eps=0.05,
                    train_freq=1,
                    gradient_steps=1,
                    target_update_interval=50,
                    verbose=1,
                    tensorboard_log=save_path)


        # Save a checkpoint every 1000 steps
        checkpoint_callback = CheckpointCallback(
            save_freq=1000,
            save_path=save_path,
            name_prefix="rl_model"
        )

        model.learn(int(20_000), callback=checkpoint_callback, tb_log_name="new_dqn", progress_bar=True)
        model.save(model_path)


    elif sys.argv[1] == 'test':
        save_path, model_path = get_paths()

        model = DQN.load(model_path)
        env.configure({"simulation_frequency": 15})

        action_counter = [0]*5  # It seems model only takes one action; check this
        crashes = 0
        test_runs = 100

        for _ in range(test_runs):
            state = env.reset()[0]
            done = False
            truncated = False
            while not done and not truncated:
                action = model.predict(state, deterministic=True)[0]
                next_state, reward, done, truncated, info = env.step(action)
                state = next_state
                if action == 2:
                    time.sleep(1)  # Slow down when changing lanes to better observe the behavior
                env.render()

                action_counter[action] += 1
                print('\r', action_counter, end='')  # Verify multiple actions are taken
                print(action)
                if info and info['crashed']:
                    crashes += 1

        print("\rCrashes:", crashes, "/", test_runs, "runs", f"({crashes/test_runs*100:0.1f} %)")
        env.close()
    elif sys.argv[1] == 'config':
        vehicle = env.unwrapped.vehicle
        print(f"🏎️ Vehicle Dynamics Parameters:")
        print(f"   - Kp (Gain): {getattr(vehicle, 'KP_A', 'Not found')}")
        print(f"   - Max Acceleration: {getattr(vehicle, 'ACC_MAX', 'Not found')} m/s²")
        print(f"   - Comfort Acceleration: {getattr(vehicle, 'COMFORT_ACC_MAX', 'Not found')} m/s²")
        print(f"   - Max Deceleration: {getattr(vehicle, 'COMFORT_ACC_MIN', 'Not found')} m/s²")
        print("\n" + "=" * 30)
        print("🔍 揭秘环境内部真实参数")
        print("=" * 30)

        # 1. 证据一：打印 Observation Space 的形状
        # 如果输出是 (5, 5)，说明神经网络输入层只留了 5 个位置
        print(f"1. [最强铁证] 神经网络输入形状 (Observation Space):")
        print(f"   👉 {env.observation_space.shape}")
        print(f"      (解读: 第一个数字 '{env.observation_space.shape[0]}' 就是感知车辆数)")

        # 2. 证据二：打印 Observation 模块的完整配置
        # 这里会显示 merge 之后的最终字典（包含你没写但默认生效的参数）
        print(f"\n2. [配置实录] env.config['observation'] 最终值:")
        import pprint

        # 获取底层环境配置（防止被 Wrapper 遮挡）
        real_config = env.unwrapped.config
        pprint.pprint(real_config['observation'])

        # 3. 证据三：对比两个 vehicles_count
        print(f"\n3. [关键对比] 物理 vs 感知:")
        print(f"   🚗 物理生成数量 (env.config['vehicles_count']): {real_config.get('vehicles_count', '默认值')}")
        print(
            f"   👀 神经感知数量 (env.config['observation']['vehicles_count']): {real_config['observation'].get('vehicles_count', '默认值')}")



    env.close()

