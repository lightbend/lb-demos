import subprocess
import argparse

def get_deployment_pods(app_name):
    lines = subprocess.check_output(['kubectl', 'get', 'pods', '--selector=appName=' + app_name]).split("\n")
    lines.pop(0) # pop header row
    pods = []
    for line in lines:
        tokens = line.split()
        if len(tokens) > 0:
            pods.append(tokens[0])

    return pods

def get_pod_internal_ips(pod_name):
    lines = subprocess.check_output(["kubectl", "exec", pod_name, "--", "cat", "/etc/hosts"]).split("\n")
    tokens = lines[-2].split()
    return tokens[0]

def generate_commands(pod_ips, partition_1, partition_2, action):
    route_action = 'add' if action == "split" else "delete"
    commands = []
    for p1_node in partition_1:
        for p2_node in partition_2:
            commands.append(['kubectl', 'exec', p1_node, '--', 'route', route_action, '-host', pod_ips[p2_node], 'reject'])
            commands.append(['kubectl', 'exec', p2_node, '--', 'route', route_action, '-host', pod_ips[p1_node], 'reject'])
    return commands

# parse arguments
parser = argparse.ArgumentParser(description='utility to simulate network partitions in minikube')
parser.add_argument('action', metavar='action', type=str, help='action to take on cluster.  either split|restore')
parser.add_argument('-a', dest='app_name', type=str, required=True, help='appName selector for pods')
parser.add_argument('-s', dest='split', type=int, required=True, help='number of pods you wish to split off')

args = parser.parse_args()

# retrieve internal pod ips
pods = get_deployment_pods(args.app_name)
pod_ips = {}
for pod in pods:
    pod_ips[pod] = get_pod_internal_ips(pod)

# execute routing rules per node
partition_commands = generate_commands(pod_ips, pods[:args.split], pods[args.split:], args.action)
for command in partition_commands:
    print "running command: " + str(command)
    subprocess.check_output(command)